package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.ResourceInfo;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PublishedRelatedResourcesManager {

  private static final Logger LOG = LoggerFactory.getLogger(PublishedRelatedResourcesManager.class);
  private static final String BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES = "/var/streamx/connector/sling/resources/published";
  private static final String INTERMEDIATE_NODE_NAME_BETWEEN_PARENT_AND_RELATED_RESOURCE_PATHS = "/related-resources";
  private static final String SLING_FOLDER = "sling:Folder";
  private static final String NT_UNSTRUCTURED = "nt:unstructured";

  private PublishedRelatedResourcesManager() {
    // no instances
  }

  static void updatePublishedResourcesData(Map<String, Set<ResourceInfo>> relatedResources, ResourceResolver resourceResolver) {
    try {
      Session session = getSession(resourceResolver);
      for (Entry<String, Set<ResourceInfo>> relatedResourcesOfParent : relatedResources.entrySet()) {
        String parentResourcePath = relatedResourcesOfParent.getKey();
        for (ResourceInfo relatedResource : relatedResourcesOfParent.getValue()) {
          String expectedRelatedResourceJcrPath = String.join("",
              parentResourceJcrPath(parentResourcePath),
              INTERMEDIATE_NODE_NAME_BETWEEN_PARENT_AND_RELATED_RESOURCE_PATHS,
              relatedResource.getPath()
          );

          if (resourceResolver.getResource(expectedRelatedResourceJcrPath) == null) {
            JcrUtils.getOrCreateByPath(expectedRelatedResourceJcrPath, SLING_FOLDER, NT_UNSTRUCTURED, session, false);
          }
        }
      }
      session.save();
    } catch (Exception ex) {
      LOG.error("Error updating JCR state for related resources", ex);
    }
  }

  static void removePublishedResourcesData(ResourceInfo parentResource, ResourceResolver resourceResolver) {
    String parentResourceJcrPath = parentResourceJcrPath(parentResource.getPath());

    try {
      Resource jcrResource = resourceResolver.getResource(parentResourceJcrPath);
      if (jcrResource != null) {
        resourceResolver.delete(jcrResource);
        resourceResolver.commit();
      }
    } catch (Exception ex) {
      LOG.error("Error updating JCR state for parent resource", ex);
    }
  }

  // TODO implement unpublish of images not present anymore in edited version of a html file
  static boolean isReferencedByOtherPublishedResource(ResourceInfo relatedResource, ResourceResolver resourceResolver) {
    try {
      // There is a resource with related resources containing the input related resource: means it is referenced
      Session session = getSession(resourceResolver);
      // TODO verify indexes on paths - check how it works, if paths are indexed by default
      // TODO verify ISCHILDNODE and ISDESCENDANTNODE if they are fast
      // TODO test on big node structure - will it fail
      String queryString = (
          "SELECT [jcr:path] " +
          "  FROM [nt:base] " +
          " WHERE ISDESCENDANTNODE([:baseJcrPath]) " +
          "   AND [jcr:path] LIKE '%:relatedResourcePath'"
      ).replace(":baseJcrPath", BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES)
       .replace(":relatedResourcePath", escapeJcrQueryParam(relatedResource.getPath()));

      return anyNodeMatches(queryString, session);
    } catch (Exception ex) {
      LOG.error("Error verifying JCR state of related resource", ex);
      return true;
    }
  }

  private static Session getSession(ResourceResolver resourceResolver) {
    return Objects.requireNonNull(resourceResolver.adaptTo(Session.class));
  }

  private static String parentResourceJcrPath(String resourcePath) {
    return BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES + resourcePath;
  }

  private static boolean anyNodeMatches(String jcrSqlQuery, Session session) throws RepositoryException {
    // TODO optimize, extract to static constants, bind values
    QueryManager queryManager = session.getWorkspace().getQueryManager();
    Query query = queryManager.createQuery(jcrSqlQuery, Query.JCR_SQL2);
    QueryResult queryResult = query.execute();
    NodeIterator resultNodes = queryResult.getNodes();
    return resultNodes.hasNext();
  }

  private static CharSequence escapeJcrQueryParam(String jcrValue) {
    return jcrValue.replace("'", "''");
  }

}
