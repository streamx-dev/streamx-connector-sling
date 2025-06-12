package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.ResourceInfo;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
  private static final String BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES = "/var/streamx/connector/sling/resources/published";
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

        // both maps of: full path in the /var structure + primaryNodeType
        Map<String, String> existingJcrRelatedResources = collectRelatedResources(relatedResourcesNode);
        Map<String, String> newJcrRelatedResources = relatedResources.stream()
            .collect(Collectors.toMap(
                resource -> relatedResourcesNodeJcrPath + resource.getPath(),
                ResourceInfo::getPrimaryNodeType,
                (u, v) -> u,
                LinkedHashMap::new
            ));

        Map<String, String> relatedResourceToAddToJcr = itemsOnlyInFirstMap(newJcrRelatedResources, existingJcrRelatedResources);
        Map<String, String> relatedResourceToDeleteFromJcr = itemsOnlyInFirstMap(existingJcrRelatedResources, newJcrRelatedResources);

        for (Map.Entry<String, String> relatedResourcePathToAdd : relatedResourceToAddToJcr.entrySet()) {
          Node relatedResourceNode = JcrUtils.getOrCreateByPath(relatedResourcePathToAdd.getKey(), "sling:Folder", "nt:unstructured", session, false);
          relatedResourceNode.setProperty(PN_PRIMARY_NODE_TYPE, relatedResourcePathToAdd.getValue());
        }

        for (Map.Entry<String, String> relatedResourcePathToDelete : relatedResourceToDeleteFromJcr.entrySet()) {
          String relatedResourceFullPath = relatedResourcePathToDelete.getKey();
          session.removeItem(relatedResourceFullPath);

          String relatedResourceOriginalPath = StringUtils.substringAfterLast(relatedResourceFullPath, relatedResourcesNodeJcrPath);
          String primaryNodeType = relatedResourcePathToDelete.getValue();
          disappearedRelatedResources.add(new ResourceInfo(relatedResourceOriginalPath, primaryNodeType));
        }
      }
      session.save();
    } catch (Exception ex) {
      LOG.error("Error updating JCR state for related resources", ex);
    }
    return disappearedRelatedResources;
  }

  private static Map<String, String> collectRelatedResources(Node root) throws RepositoryException {
    Map<String, String> relatedResources = new LinkedHashMap<>();
    collectRelatedResources(root, relatedResources);
    return relatedResources;
  }

  private static void collectRelatedResources(Node node, Map<String, String> relatedResources) throws RepositoryException {
    if (node.hasNodes()) {
      NodeIterator childNodes = node.getNodes();
      while (childNodes.hasNext()) {
        Node child = childNodes.nextNode();
        collectRelatedResources(child, relatedResources);
      }
    } else {
      if (node.hasProperty(PN_PRIMARY_NODE_TYPE)) {
        relatedResources.put(node.getPath(), node.getProperty(PN_PRIMARY_NODE_TYPE).getString());
      }
    }
  }

  private static Map<String, String> itemsOnlyInFirstMap(Map<String, String> map1, Map<String, String> map2) {
    Map<String, String> result = new LinkedHashMap<>(map1);
    result.keySet().removeAll(map2.keySet());
    return result;
  }

  static void removePublishedResourcesData(ResourceInfo parentResource, ResourceResolver resourceResolver) {
    String parentResourceJcrPath = BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES + parentResource.getPath();

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
