package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.ResourceInfo;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFactory;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PublishedRelatedResourcesManager {

  private static final Logger LOG = LoggerFactory.getLogger(PublishedRelatedResourcesManager.class);
  private static final String BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES = "/var/streamx/connector/sling/resources/published/grouped-by-parent-resource-path";
  private static final String INTERMEDIATE_NODE_NAME_BETWEEN_PARENT_AND_RELATED_RESOURCE_PATHS = "/related-resources";
  private static final String PN_PRIMARY_NODE_TYPE = "primaryNodeType";

  private PublishedRelatedResourcesManager() {
    // no instances
  }

  /**
   * Updates published resources data and returns set of related resources that have disappeared from the content of the parent resources.
   * For example, if page-1.html contained references to image-1.png and image-2.png when published before,
   * and now it contains only image-1.png (due to the page being edited) - the method will return image-2.png.
   * The returned set contains sum of all disappeared related resources from all the parent pages.
   * It's up to the caller to verify if the returned related resources can be safely unpublished.
   */
  static Set<ResourceInfo> updatePublishedResourcesData(Map<String, Set<ResourceInfo>> relatedResourcesByParentPath, ResourceResolver resourceResolver) {
    Set<ResourceInfo> disappearedRelatedResources = new LinkedHashSet<>();
    try {
      Session session = getSession(resourceResolver);
      for (Entry<String, Set<ResourceInfo>> relatedResourcesForParentPath : relatedResourcesByParentPath.entrySet()) {
        String parentResourcePath = relatedResourcesForParentPath.getKey();
        Set<ResourceInfo> relatedResources = relatedResourcesForParentPath.getValue();

        String relatedResourcesNodeJcrPath =
            BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES
            + parentResourcePath
            + INTERMEDIATE_NODE_NAME_BETWEEN_PARENT_AND_RELATED_RESOURCE_PATHS;
        Node relatedResourcesNode = JcrUtils.getOrCreateByPath(relatedResourcesNodeJcrPath, "sling:Folder", session);

        Set<ResourceInfo> existingRelatedResources = collectRelatedResources(relatedResourcesNode);

        Set<ResourceInfo> relatedResourcesToAddToJcr = itemsOnlyInFirstSet(relatedResources, existingRelatedResources);
        for (ResourceInfo resource : relatedResourcesToAddToJcr) {
          Node relatedResourceNode = JcrUtils.getOrCreateByPath(relatedResourcesNodeJcrPath + resource.getPath(), "sling:Folder", "nt:unstructured", session, false);
          relatedResourceNode.setProperty(PN_PRIMARY_NODE_TYPE, resource.getPrimaryNodeType());
        }

        Set<ResourceInfo> relatedResourcesToDeleteFromJcr = itemsOnlyInFirstSet(existingRelatedResources, relatedResources);
        for (ResourceInfo resource : relatedResourcesToDeleteFromJcr) {
          String relatedResourceFullPath = relatedResourcesNodeJcrPath + resource.getPath();
          session.removeItem(relatedResourceFullPath);
        }

        disappearedRelatedResources.addAll(relatedResourcesToDeleteFromJcr);
      }
      session.save();
    } catch (Exception ex) {
      LOG.error("Error updating JCR state for related resources", ex);
    }
    return disappearedRelatedResources;
  }

  private static Set<ResourceInfo> collectRelatedResources(Node root) throws RepositoryException {
    Set<ResourceInfo> relatedResources = new LinkedHashSet<>();
    collectRelatedResources(root.getPath(), root, relatedResources);
    return relatedResources;
  }

  private static void collectRelatedResources(String rootPath, Node node, Set<ResourceInfo> relatedResources) throws RepositoryException {
    if (node.hasNodes()) {
      NodeIterator childNodes = node.getNodes();
      while (childNodes.hasNext()) {
        Node child = childNodes.nextNode();
        collectRelatedResources(rootPath, child, relatedResources);
      }
    } else {
      if (node.hasProperty(PN_PRIMARY_NODE_TYPE)) {
        String nodeRelativePath = StringUtils.substringAfter(node.getPath(), rootPath);
        String primaryNodeType = node.getProperty(PN_PRIMARY_NODE_TYPE).getString();
        relatedResources.add(new ResourceInfo(nodeRelativePath, primaryNodeType));
      }
    }
  }

  private static <T> Set<T> itemsOnlyInFirstSet(Set<T> set1, Set<T> set2) {
    Set<T> result = new LinkedHashSet<>(set1);
    result.removeAll(set2);
    return result;
  }

  static void removePublishedResourcesData(List<ResourceInfo> parentResources, ResourceResolver resourceResolver) {
    try {
      for (ResourceInfo resource : parentResources) {
        String parentResourceJcrPath = BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES + resource.getPath();
        Resource jcrResource = resourceResolver.getResource(parentResourceJcrPath);
        if (jcrResource != null) {
          resourceResolver.delete(jcrResource);
        }
      }
      resourceResolver.commit();
    } catch (Exception ex) {
      LOG.error("Error updating JCR state for parent resource", ex);
    }
  }

  static Set<ResourceInfo> filterUnreferencedResources(Set<ResourceInfo> relatedResources, ResourceResolver resourceResolver) {
    String queryString =
        "SELECT [jcr:path] " +
        "  FROM [nt:base] " +
        " WHERE ISDESCENDANTNODE([" + BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES + "]) " +
        "   AND [jcr:path] LIKE $relatedResourcePath";

    Set<ResourceInfo> unreferencedResources = new LinkedHashSet<>();
    try {
      Session session = getSession(resourceResolver);
      QueryManager queryManager = session.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery(queryString, Query.JCR_SQL2);
      ValueFactory valueFactory = session.getValueFactory();

      for (ResourceInfo relatedResource : relatedResources) {
        query.bindValue("relatedResourcePath", valueFactory.createValue("%" + relatedResource.getPath()));
        query.setLimit(1);
        NodeIterator resultNodes = query.execute().getNodes();
        if (!resultNodes.hasNext()) {
          unreferencedResources.add(relatedResource);
        }
      }
      return unreferencedResources;
    } catch (Exception ex) {
      LOG.error("Error verifying JCR state of related resource", ex);
      return Collections.emptySet();
    }
  }

  private static Session getSession(ResourceResolver resourceResolver) {
    return Objects.requireNonNull(resourceResolver.adaptTo(Session.class));
  }
}
