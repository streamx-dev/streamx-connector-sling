package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.ResourceInfo;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <pre>
 * This class manages information about published resources and related resources in JCR.
 * Usage: while ingesting resources and their related resources.
 * How to use:
 *  1. Ingest main resource.
 *  2. Call {@link #updatePublishedResources(ResourceInfo parentResource, PublicationAction action)}.
 *  3. If the resource has related resources, ingest them.
 *  4. While attempting to unpublish related resources, you should call {@link #isReferencedByOtherResource(ResourceInfo relatedResource)} to verify if a related resource can be unpublished.
 *  4. Call {@link #updatePublishedResources(Map relatedResources, PublicationAction action)}.
 *     The map should contain data used for related resources ingestion, with entries of: parent resource path + set of related resources for the parent resource.
 *  </pre>
 */
public class PublishedResourcesManager {

  private static final Logger LOG = LoggerFactory.getLogger(PublishedResourcesManager.class);
  private static final String BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES = "/var/streamx/connector/sling/resources/published";
  private static final String SLING_FOLDER = "sling:Folder";
  private static final String NT_UNSTRUCTURED = "nt:unstructured";

  private final ResourceResolverFactory resourceResolverFactory;

  @Activate
  PublishedResourcesManager(@Reference ResourceResolverFactory resourceResolverFactory) {
    this.resourceResolverFactory = resourceResolverFactory;
  }

  void updatePublishedResources(ResourceInfo parentResource, PublicationAction action) {
    String parentResourceJcrPath = parentResourceJcrPath(parentResource.getPath());

    try (ResourceResolver resourceResolver = getResourceResolver()) {
      Resource jcrResource = resourceResolver.getResource(parentResourceJcrPath);
      if (action == PublicationAction.PUBLISH && jcrResource == null) {
        Session session = getSession(resourceResolver);
        getOrCreateParentResourceJcrNode(parentResourceJcrPath, session);
      } else if (action == PublicationAction.UNPUBLISH && jcrResource != null) {
        resourceResolver.delete(jcrResource);
        resourceResolver.commit();
      }
    } catch (Exception ex) {
      LOG.error("Error updating JCR state for parent resource", ex);
    }
  }

  void updatePublishedResources(Map<String, Set<ResourceInfo>> relatedResources, PublicationAction action) {
    if (action == PublicationAction.PUBLISH) {
      try (ResourceResolver resourceResolver = getResourceResolver()) {
        Session session = getSession(resourceResolver);
        for (Entry<String, Set<ResourceInfo>> relatedResourcesOfParent : relatedResources.entrySet()) {
          String parentResourcePath = relatedResourcesOfParent.getKey();
          for (ResourceInfo relatedResource : relatedResourcesOfParent.getValue()) {
            String parentResourceJcrPath = parentResourceJcrPath(parentResourcePath);

            // add the node only if not there yet
            String selectExistingNodes = String.join("\n",
                    "SELECT relatedResource.*",
                    "  FROM [nt:base] AS parentResource",
                    "  INNER JOIN [nt:base] AS relatedResource ON ISCHILDNODE(relatedResource, parentResource)",
                    "  WHERE parentResource.[jcr:path] = ':parentResourceJcrPath'",
                    "   AND relatedResource.path = ':relatedResourcePath'",
                    "   AND relatedResource.primaryNodeType = ':relatedResourcePrimaryNodeType'")
                .replace(":parentResourceJcrPath", escapeJcrQueryParam(parentResourceJcrPath))
                .replace(":relatedResourcePath", escapeJcrQueryParam(relatedResource.getPath()))
                .replace(":relatedResourcePrimaryNodeType", escapeJcrQueryParam(relatedResource.getPrimaryNodeType()));

            if (noMatchingNode(selectExistingNodes, session)) {
              Node parentResourceNode = getOrCreateParentResourceJcrNode(parentResourceJcrPath, session);
              Node relatedResourceNode = parentResourceNode.addNode("relatedResource-" + UUID.randomUUID(), NT_UNSTRUCTURED);
              relatedResourceNode.setProperty("path", relatedResource.getPath());
              relatedResourceNode.setProperty("primaryNodeType", relatedResource.getPrimaryNodeType());
            }
          }
        }
        session.save();
      } catch (Exception ex) {
        LOG.error("Error updating JCR state for related resources", ex);
      }
    }
    // note: if the methods are used as described in javadoc - entry for parent resource path is already removed from JCR, so nothing to do for UNPUBLISH action here
  }

  boolean isReferencedByOtherResource(ResourceInfo relatedResource) {
    try (ResourceResolver resourceResolver = getResourceResolver()) {
      // Case 1: there is a directly published parent resource with the same path as the input related resource: means it is referenced
      Session session = getSession(resourceResolver);
      String parentResourceJcrPath = parentResourceJcrPath(relatedResource.getPath());
      Node potentialParentResourceNode = JcrUtils.getNodeIfExists(parentResourceJcrPath, session);
      if (potentialParentResourceNode != null && !potentialParentResourceNode.hasNodes()) {
        return true;
      }

      // Case 2: there is a resource with related resources containing the input related resource: means it is referenced
      String queryString = String.join("\n",
              "SELECT parentResource.*",
              "  FROM [nt:base] AS parentResource",
              "  INNER JOIN [nt:base] AS relatedResource ON ISCHILDNODE(relatedResource, parentResource)",
              " WHERE ISDESCENDANTNODE(parentResource, ':baseJcrPath')",
              "   AND relatedResource.path = ':relatedResourcePath'",
              "   AND relatedResource.primaryNodeType = ':relatedResourcePrimaryNodeType'")
          .replace(":baseJcrPath", escapeJcrQueryParam(BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES))
          .replace(":relatedResourcePath", escapeJcrQueryParam(relatedResource.getPath()))
          .replace(":relatedResourcePrimaryNodeType", escapeJcrQueryParam(relatedResource.getPrimaryNodeType()));

      return anyNodeMatches(queryString, session);
    } catch (Exception ex) {
      LOG.error("Error verifying JCR state", ex);
      return false;
    }
  }

  @SuppressWarnings("deprecation")
  private ResourceResolver getResourceResolver() throws LoginException {
    return resourceResolverFactory.getAdministrativeResourceResolver(null);
  }

  private static Session getSession(ResourceResolver resourceResolver) {
    return Objects.requireNonNull(resourceResolver.adaptTo(Session.class));
  }

  private static String parentResourceJcrPath(String resourcePath) {
    return BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES + resourcePath;
  }

  private static Node getOrCreateParentResourceJcrNode(String parentResourceJcrPath, Session session) throws RepositoryException {
    return JcrUtils.getOrCreateByPath(parentResourceJcrPath, SLING_FOLDER, NT_UNSTRUCTURED, session, true);
  }

  private static boolean anyNodeMatches(String jcrSqlQuery, Session session) throws RepositoryException {
    QueryManager queryManager = session.getWorkspace().getQueryManager();
    Query query = queryManager.createQuery(jcrSqlQuery, Query.JCR_SQL2);
    QueryResult queryResult = query.execute();
    NodeIterator resultNodes = queryResult.getNodes();
    return resultNodes.hasNext();
  }

  private static boolean noMatchingNode(String jcrSqlQuery, Session session) throws RepositoryException {
    return !anyNodeMatches(jcrSqlQuery, session);
  }

  private static CharSequence escapeJcrQueryParam(String jcrValue) {
    return jcrValue.replace("'", "''");
  }

}
