package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.ResourceInfo;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PublishedRelatedResourcesManager {

  private static final Logger LOG = LoggerFactory.getLogger(PublishedRelatedResourcesManager.class);
  private static final String BASE_NODE_PATH = "/var/streamx/connector/sling/referenced-related-resources";
  private static final String PN_RELATED_RESOURCES = "relatedResources";

  private PublishedRelatedResourcesManager() {
    // no instances
  }

  /**
   * Updates published resources data in the internal JCR registries under /var.
   * @return Map of related resources that have disappeared from the content of the parent resources.
   * For example, if page-1.html contained references to image-1.png and image-2.png when published before,
   * and now it contains only image-1.png (due to the page being edited) - the method will return image-2.png.
   * The returned set contains sum of all disappeared related resources from all the parent pages.
   * It's up to the caller to verify if the returned related resources can be safely unpublished.
   */
  static Map<String, Set<ResourceInfo>> updatePublishedResourcesData(Map<String, Set<ResourceInfo>> relatedResourcesByParentPath, ResourceResolver resourceResolver) {
    Map<String, Set<ResourceInfo>> disappearedRelatedResources = new LinkedHashMap<>();
    try {
      Session session = getSession(resourceResolver);
      for (Entry<String, Set<ResourceInfo>> relatedResourcesForParentPath : relatedResourcesByParentPath.entrySet()) {
        String parentResourcePath = relatedResourcesForParentPath.getKey();
        Set<ResourceInfo> relatedResources = relatedResourcesForParentPath.getValue();
        updatePublishedResourcesData(parentResourcePath, relatedResources, session, disappearedRelatedResources);
      }
      session.save();
    } catch (Exception ex) {
      LOG.error("Error updating JCR state for related resources", ex);
    }
    return disappearedRelatedResources;
  }

  private static void updatePublishedResourcesData(String parentResourcePath, Set<ResourceInfo> relatedResources, Session session,
      Map<String, Set<ResourceInfo>> disappearedRelatedResources) throws RepositoryException {
    String parentResourceJcrPath = BASE_NODE_PATH + parentResourcePath;

    Node parentResourceJcrNode;
    Set<String> relatedResourcesInJcr;
    if (session.nodeExists(parentResourceJcrPath)) {
      parentResourceJcrNode = session.getNode(parentResourceJcrPath);
      relatedResourcesInJcr = collectRelatedResources(parentResourceJcrNode);
    } else {
      parentResourceJcrNode = JcrUtils.getOrCreateByPath(parentResourceJcrPath, "nt:unstructured", session);
      relatedResourcesInJcr = new LinkedHashSet<>();
    }

    Set<String> relatedResourcesToProcess = SetUtils.mapToLinkedHashSet(relatedResources, ResourceInfo::getPath);

    Set<String> relatedResourcesToAddToJcr = itemsOnlyInFirstSet(relatedResourcesToProcess, relatedResourcesInJcr);
    relatedResourcesInJcr.addAll(relatedResourcesToAddToJcr);
    PublishedRelatedResourcesInversedTreeManager.addData(relatedResourcesToAddToJcr, session);

    Set<String> relatedResourcesToDeleteFromJcr = itemsOnlyInFirstSet(relatedResourcesInJcr, relatedResourcesToProcess);
    relatedResourcesInJcr.removeAll(relatedResourcesToDeleteFromJcr);
    PublishedRelatedResourcesInversedTreeManager.removeData(relatedResourcesToDeleteFromJcr, parentResourcePath, session);

    if (relatedResourcesInJcr.isEmpty()) {
      parentResourceJcrNode.setProperty(PN_RELATED_RESOURCES, (String[]) null);
    } else {
      String[] relatedResourcesArray = relatedResourcesInJcr.toArray(String[]::new);
      parentResourceJcrNode.setProperty(PN_RELATED_RESOURCES, relatedResourcesArray);
    }

    if (!relatedResourcesToDeleteFromJcr.isEmpty()) {
      disappearedRelatedResources.put(
          parentResourcePath,
          SetUtils.mapToLinkedHashSet(relatedResourcesToDeleteFromJcr, ResourceInfo::new)
      );
    }
  }

  private static Set<String> collectRelatedResources(Node parentResourceJcrNode) throws RepositoryException {
    Set<String> relatedResources = new LinkedHashSet<>();
    if (parentResourceJcrNode.hasProperty(PN_RELATED_RESOURCES)) {
      Property property = parentResourceJcrNode.getProperty(PN_RELATED_RESOURCES);
      for (Value value : property.getValues()) {
        relatedResources.add(value.getString());
      }
    }
    return relatedResources;
  }

  static void removePublishedResourcesData(List<ResourceInfo> parentResources, ResourceResolver resourceResolver) {
    try {
      Session session = getSession(resourceResolver);
      for (ResourceInfo parentResource : parentResources) {
        String parentResourcePath = parentResource.getPath();
        String parentResourceJcrPath = BASE_NODE_PATH + parentResourcePath;
        if (session.nodeExists(parentResourceJcrPath)) {
          Node parentResourceJcrNode = session.getNode(parentResourceJcrPath);
          Set<String> relatedResources = collectRelatedResources(parentResourceJcrNode);
          PublishedRelatedResourcesInversedTreeManager.removeData(relatedResources, parentResourcePath, session);
          if (parentResourceJcrNode.hasProperty(PN_RELATED_RESOURCES)) {
            if (parentResourceJcrNode.hasNodes()) {
              parentResourceJcrNode.setProperty(PN_RELATED_RESOURCES, (String[]) null);
            } else {
              parentResourceJcrNode.remove();
            }
          }
        }
      }
      session.save();
    } catch (Exception ex) {
      LOG.error("Error updating JCR state for parent resources {}", parentResources, ex);
    }
  }

  static boolean wasPublished(ResourceInfo relatedResource, ResourceResolver resourceResolver) {
    try {
      Session session = getSession(resourceResolver);
      return PublishedRelatedResourcesInversedTreeManager.wasPublished(relatedResource.getPath(), session);
    } catch (Exception ex) {
      LOG.error("Error checking if resource was published", ex);
      return false;
    }
  }

  static Session getSession(ResourceResolver resourceResolver) {
    return Objects.requireNonNull(resourceResolver.adaptTo(Session.class));
  }

  private static Set<String> itemsOnlyInFirstSet(Set<String> set1, Set<String> set2) {
    Set<String> result = new LinkedHashSet<>(set1);
    result.removeAll(set2);
    return result;
  }
}
