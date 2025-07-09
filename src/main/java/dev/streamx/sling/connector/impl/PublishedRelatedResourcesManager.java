package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.ResourceInfo;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PublishedRelatedResourcesManager {

  static final String BASE_NODE_PATH = "/var/streamx/connector/sling/referenced-related-resources";
  private static final String PN_RELATED_RESOURCES = "relatedResources";
  private static final Logger LOG = LoggerFactory.getLogger(PublishedRelatedResourcesManager.class);

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
  static Map<String, Set<ResourceInfo>> updatePublishedResourcesData(Map<String, Set<ResourceInfo>> relatedResourcesByParentPath, Session session)
      throws RepositoryException {
    Map<String, Set<ResourceInfo>> disappearedRelatedResources = new LinkedHashMap<>();
    for (Entry<String, Set<ResourceInfo>> relatedResourcesForParentPath : relatedResourcesByParentPath.entrySet()) {
      String parentResourcePath = relatedResourcesForParentPath.getKey();
      Set<ResourceInfo> relatedResources = relatedResourcesForParentPath.getValue();
      updatePublishedResourcesData(parentResourcePath, relatedResources, session, disappearedRelatedResources);
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
      parentResourceJcrNode = JcrNodeHelper.createNode(parentResourceJcrPath, session);
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
      unsetRelatedResourcesProperty(parentResourceJcrNode);
    } else {
      setRelatedResourcesProperty(parentResourceJcrNode, relatedResourcesInJcr);
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

  static void removePublishedResourcesData(List<ResourceInfo> parentResources, Session session)
      throws RepositoryException {
    for (ResourceInfo parentResource : parentResources) {
      String parentResourcePath = parentResource.getPath();
      String parentResourceJcrPath = BASE_NODE_PATH + parentResourcePath;
      if (session.nodeExists(parentResourceJcrPath)) {
        Node parentResourceJcrNode = session.getNode(parentResourceJcrPath);
        Set<String> relatedResources = collectRelatedResources(parentResourceJcrNode);
        PublishedRelatedResourcesInversedTreeManager.removeData(relatedResources, parentResourcePath, session);
        if (parentResourceJcrNode.hasProperty(PN_RELATED_RESOURCES)) {
          if (parentResourceJcrNode.hasNodes()) {
            unsetRelatedResourcesProperty(parentResourceJcrNode);
          } else {
            JcrNodeHelper.removeNodeAlongWithOrphanedParents(parentResourceJcrNode, BASE_NODE_PATH);
          }
        }
      }
    }
  }

  static void removePublishedResourcesData(Map<String, Set<ResourceInfo>> relatedResourcesByParentPath, Session session)
      throws RepositoryException {
    for (Entry<String, Set<ResourceInfo>> relatedResourcesForParentPath : relatedResourcesByParentPath.entrySet()) {
      String parentResourcePath = relatedResourcesForParentPath.getKey();
      String parentResourceJcrPath = BASE_NODE_PATH + parentResourcePath;
      if (session.nodeExists(parentResourceJcrPath)) {
        Node parentResourceJcrNode = session.getNode(parentResourceJcrPath);
        Set<String> currentRelatedResources = collectRelatedResources(parentResourceJcrNode);
        Set<ResourceInfo> relatedResourcesToRemove = relatedResourcesForParentPath.getValue();
        Set<String> relatedResourcesToLeaveInJcr = subtractResources(currentRelatedResources, relatedResourcesToRemove);
        if (relatedResourcesToLeaveInJcr.isEmpty()) {
          JcrNodeHelper.removeNodeAlongWithOrphanedParents(parentResourceJcrNode, BASE_NODE_PATH);
        } else {
          setRelatedResourcesProperty(parentResourceJcrNode, relatedResourcesToLeaveInJcr);
        }
      }
    }
  }

  private static void setRelatedResourcesProperty(Node parentResourceJcrNode, Set<String> relatedResources) throws RepositoryException {
    String[] relatedResourcesArray = relatedResources.toArray(String[]::new);
    parentResourceJcrNode.setProperty(PN_RELATED_RESOURCES, relatedResourcesArray);
  }

  private static void unsetRelatedResourcesProperty(Node parentResourceJcrNode) throws RepositoryException {
    parentResourceJcrNode.setProperty(PN_RELATED_RESOURCES, (String[]) null);
  }

  static boolean wasPublished(ResourceInfo relatedResource, Session session) {
    try {
      return PublishedRelatedResourcesInversedTreeManager.wasPublished(relatedResource.getPath(), session);
    } catch (Exception ex) {
      LOG.error("Error checking if resource was published", ex);
      return false;
    }
  }

  private static Set<String> itemsOnlyInFirstSet(Set<String> set1, Set<String> set2) {
    Set<String> result = new LinkedHashSet<>(set1);
    result.removeAll(set2);
    return result;
  }

  private static Set<String> subtractResources(Set<String> resourcePaths, Set<ResourceInfo> resourcesToSubtract) {
    Set<String> result = new LinkedHashSet<>(resourcePaths);
    for (ResourceInfo resourceToSubtract : resourcesToSubtract) {
      result.remove(resourceToSubtract.getPath());
    }
    return result;
  }
}
