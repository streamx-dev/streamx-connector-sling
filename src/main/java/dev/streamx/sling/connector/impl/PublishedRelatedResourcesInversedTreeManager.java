package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.ResourceInfo;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

/**
 * This class serves as a manager of inversed tree that PublishedRelatedResourcesManager produces.
 * Methods of this class should be called in transaction with methods that modify the original tree,
 * so that both trees are always in a consistent state.
 */
final class PublishedRelatedResourcesInversedTreeManager {

  private static final String BASE_NODE_PATH = "/var/streamx/connector/sling/resources/published/grouped-by-related-resource-path";
  private static final String PN_PARENT_RESOURCES = "parentResources";

  private PublishedRelatedResourcesInversedTreeManager() {
    // no instances
  }

  static void addData(String parentResourcePath, Set<ResourceInfo> relatedResources, Session session) throws RepositoryException {
    for (ResourceInfo relatedResource : relatedResources) {
      String relatedResourceJcrPath = BASE_NODE_PATH + relatedResource.getPath();
      Node relatedResourceJcrNode = JcrUtils.getOrCreateByPath(relatedResourceJcrPath, "sling:Folder", "nt:unstructured", session, false);

      Set<String> parentResourcePaths = getParentResourcePaths(relatedResourceJcrNode);
      if (!parentResourcePaths.contains(parentResourcePath)) {
        parentResourcePaths.add(parentResourcePath);
        setParentResourcePaths(relatedResourceJcrNode, parentResourcePaths);
      }
    }
  }

  static void removeData(String parentResourcePath, Set<ResourceInfo> relatedResources, ResourceResolver resourceResolver) throws RepositoryException {
    for (ResourceInfo relatedResource : relatedResources) {
      String relatedResourceJcrPath = BASE_NODE_PATH + relatedResource.getPath();
      Resource relatedResourceJcrResource = resourceResolver.getResource(relatedResourceJcrPath);
      if (relatedResourceJcrResource != null) {
        Node relatedResourceJcrNode = Objects.requireNonNull(relatedResourceJcrResource.adaptTo(Node.class));
        Set<String> parentResourcePaths = getParentResourcePaths(relatedResourceJcrNode);
        if (parentResourcePaths.contains(parentResourcePath)) {
          parentResourcePaths.remove(parentResourcePath);
          setParentResourcePaths(relatedResourceJcrNode, parentResourcePaths);
        }
      }
    }
  }

  static Set<ResourceInfo> filterUnreferencedResources(Set<ResourceInfo> relatedResources, ResourceResolver resourceResolver) throws RepositoryException {
    Set<ResourceInfo> unreferencedResources = new LinkedHashSet<>();
    for (ResourceInfo relatedResource : relatedResources) {
      String relatedResourceJcrPath = BASE_NODE_PATH + relatedResource.getPath();
      Resource relatedResourceJcrResource = resourceResolver.getResource(relatedResourceJcrPath);
      if (relatedResourceJcrResource != null) {
        Node relatedResourceJcrNode = Objects.requireNonNull(relatedResourceJcrResource.adaptTo(Node.class));
        Set<String> parentResourcePaths = getParentResourcePaths(relatedResourceJcrNode);
        if (parentResourcePaths.isEmpty()) {
          unreferencedResources.add(relatedResource);
        }
      }
    }
    return unreferencedResources;
  }

  private static Set<String> getParentResourcePaths(Node relatedResourceJcrNode) throws RepositoryException {
    LinkedHashSet<String> parentResourcePaths = new LinkedHashSet<>();
    if (relatedResourceJcrNode.hasProperty(PN_PARENT_RESOURCES)) {
      for (Value value : relatedResourceJcrNode.getProperty(PN_PARENT_RESOURCES).getValues()) {
        parentResourcePaths.add(value.getString());
      }
    }
    return parentResourcePaths;
  }

  private static void setParentResourcePaths(Node relatedResourceJcrNode, Set<String> parentResourcePaths) throws RepositoryException {
    relatedResourceJcrNode.setProperty(PN_PARENT_RESOURCES, parentResourcePaths.toArray(String[]::new));
  }
}
