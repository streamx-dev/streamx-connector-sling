package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.ResourceInfo;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

/**
 * This class serves as a manager of inversed tree that PublishedRelatedResourcesManager produces.
 * Methods of this class should be called in transaction with methods that modify the original tree,
 * so that both trees are always in a consistent state.
 */
final class PublishedRelatedResourcesInversedTreeManager {

  private static final String BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES = "/var/streamx/connector/sling/resources/published/grouped-by-related-resource-path";
  private static final String INTERMEDIATE_NODE_NAME_BETWEEN_RELATED_AND_PARENT_RESOURCE_PATHS = "/parent-resources";

  private PublishedRelatedResourcesInversedTreeManager() {
    // no instances
  }

  static void addData(String parentResourcePath, Set<ResourceInfo> resources, Session session) throws RepositoryException {
    for (ResourceInfo resource : resources) {
      String jcrPathToAdd = parentResourcesJcrPath(resource) + parentResourcePath;
      // TODO: add to property values list
      JcrUtils.getOrCreateByPath(jcrPathToAdd, "sling:Folder", "nt:unstructured", session, false);
    }
  }

  static void removeData(String parentResourcePath, Set<ResourceInfo> resources, ResourceResolver resourceResolver) throws PersistenceException {
    for (ResourceInfo resource : resources) {
      String jcrPathToRemove = parentResourcesJcrPath(resource) + parentResourcePath;
      Resource existingResource = resourceResolver.getResource(jcrPathToRemove);
      if (existingResource != null) {
        resourceResolver.delete(existingResource); // TODO: change to: delete from node property values

        // delete parent nodes if empty
//        String parentResourcesNodePath = parentResourcesJcrPath(resource);
//        for(;;) {
//          jcrPathToRemove = StringUtils.substringBeforeLast(jcrPathToRemove, "/");
//          if (!jcrPathToRemove.startsWith(parentResourcesNodePath) || jcrPathToRemove.equals(parentResourcesNodePath)) {
//            break;
//          }
//          Resource parentNode = resourceResolver.getResource(jcrPathToRemove);
//          if (parentNode != null && !parentNode.hasChildren()) {
//            resourceResolver.delete(parentNode);
//          }
//        }
      }
    }
  }

  static Set<ResourceInfo> filterUnreferencedResources(Set<ResourceInfo> relatedResources, ResourceResolver resourceResolver) {
    Set<ResourceInfo> unreferencedResources = new LinkedHashSet<>();
    for (ResourceInfo relatedResource : relatedResources) {
      Resource parentResourcesNode = resourceResolver.getResource(parentResourcesJcrPath(relatedResource));
      if (!containsAnyNonFolderDescendant(parentResourcesNode)) {
        unreferencedResources.add(relatedResource);
      }
    }
    return unreferencedResources;
  }

  private static boolean containsAnyNonFolderDescendant(Resource resource) {
    if (resource == null) {
      return false;
    }
    if (!resource.getResourceType().equals("sling:Folder")) {
      return true;
    }
    for (Resource childResource : resource.getChildren()) {
      if (containsAnyNonFolderDescendant(childResource)) {
        return true;
      }
    }
    return false;
  }

  private static String parentResourcesJcrPath(ResourceInfo relatedResource) {
    return BASE_NODE_PATH_FOR_PUBLISHED_RESOURCES + relatedResource.getPath() + INTERMEDIATE_NODE_NAME_BETWEEN_RELATED_AND_PARENT_RESOURCE_PATHS;
  }
}
