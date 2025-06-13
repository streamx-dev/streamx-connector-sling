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
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PublishedRelatedResourcesManager {

  private static final Logger LOG = LoggerFactory.getLogger(PublishedRelatedResourcesManager.class);
  private static final String BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES = "/var/streamx/connector/sling/resources/published";
  private static final String PN_RELATED_RESOURCES = "relatedResources";

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

        String relatedResourcesNodeJcrPath = BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES + parentResourcePath;
        Node relatedResourcesNode = JcrUtils.getOrCreateByPath(relatedResourcesNodeJcrPath, "sling:Folder", "nt:unstructured", session, false);

        Set<ResourceInfo> relatedResourcesInJcr = collectRelatedResources(relatedResourcesNode);

        Set<ResourceInfo> relatedResourcesToAddToJcr = itemsOnlyInFirstSet(relatedResources, relatedResourcesInJcr);
        relatedResourcesInJcr.addAll(relatedResourcesToAddToJcr);

        Set<ResourceInfo> relatedResourcesToDeleteFromJcr = itemsOnlyInFirstSet(relatedResourcesInJcr, relatedResources);
        relatedResourcesInJcr.removeAll(relatedResourcesToDeleteFromJcr);

        updateRelatedResources(relatedResourcesNode, relatedResourcesInJcr);

        disappearedRelatedResources.addAll(relatedResourcesToDeleteFromJcr);
      }
      session.save();
    } catch (Exception ex) {
      LOG.error("Error updating JCR state for related resources", ex);
    }
    return disappearedRelatedResources;
  }

  private static Set<ResourceInfo> collectRelatedResources(Node relatedResourcesNode) throws RepositoryException {
    Set<ResourceInfo> relatedResources = new LinkedHashSet<>();
    if (relatedResourcesNode.hasProperty(PN_RELATED_RESOURCES)) {
      Property property = relatedResourcesNode.getProperty(PN_RELATED_RESOURCES);
      for (Value value : property.getValues()) {
        relatedResources.add(ResourceInfo.deserialize(value.getString()));
      }
    }
    return relatedResources;
  }

  private static void updateRelatedResources(Node relatedResourcesNode, Set<ResourceInfo> relatedResourcesToSet) throws RepositoryException {
    String[] valuesToSet = relatedResourcesToSet.stream()
        .map(ResourceInfo::serialize)
        .toArray(String[]::new);
    relatedResourcesNode.setProperty(PN_RELATED_RESOURCES, valuesToSet);
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
        "  FROM [nt:base] AS node " +
        " WHERE ISDESCENDANTNODE([" + BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES + "]) " +
        "   AND node.[" + PN_RELATED_RESOURCES + "] = $relatedResource";

    Set<ResourceInfo> unreferencedResources = new LinkedHashSet<>();
    try {
      Session session = getSession(resourceResolver);
      QueryManager queryManager = session.getWorkspace().getQueryManager();
      Query query = queryManager.createQuery(queryString, Query.JCR_SQL2);
      ValueFactory valueFactory = session.getValueFactory();

      for (ResourceInfo relatedResource : relatedResources) {
        query.bindValue("relatedResource", valueFactory.createValue(relatedResource.serialize()));
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

  private static <T> Set<T> itemsOnlyInFirstSet(Set<T> set1, Set<T> set2) {
    Set<T> result = new LinkedHashSet<>(set1);
    result.removeAll(set2);
    return result;
  }
}
