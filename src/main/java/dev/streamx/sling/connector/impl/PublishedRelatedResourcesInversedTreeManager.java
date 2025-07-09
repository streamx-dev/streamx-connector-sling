package dev.streamx.sling.connector.impl;

import java.util.Set;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 * This class serves as a manager of inversed tree that PublishedRelatedResourcesManager produces.
 * Methods of this class should be called in transaction with methods that modify the original tree,
 * so that both trees are always in a consistent state.
 */
final class PublishedRelatedResourcesInversedTreeManager {

  static final String BASE_NODE_PATH = "/var/streamx/connector/sling/related-resources";

  private PublishedRelatedResourcesInversedTreeManager() {
    // no instances
  }

  static void addData(Set<String> relatedResources, Session session) throws RepositoryException {
    for (String relatedResource : relatedResources) {
      String relatedResourceJcrPath = BASE_NODE_PATH + relatedResource;
      JcrNodeHelper.createNode(relatedResourceJcrPath, session);
    }
  }

  /**
   * Removes the paths of the specified related resources from the tree, but only if they are internal to the given parent resource.
   * Assuming each related resource extracted from the parent’s content has a unique, extension-based path,
   * the method should never remove any nested nodes.
   */
  static void removeData(Set<String> relatedResources, String parentResourcePath, Session session) throws RepositoryException {
    for (String relatedResource : relatedResources) {
      if (InternalResourceDetector.isInternalResource(relatedResource, parentResourcePath)) {
        String relatedResourceJcrPath = BASE_NODE_PATH + relatedResource;
        if (session.nodeExists(relatedResourceJcrPath)) {
          Node nodeToRemove = session.getNode(relatedResourceJcrPath);
          JcrNodeHelper.removeNodeAlongWithOrphanedParents(nodeToRemove, BASE_NODE_PATH);
        }
      }
    }
  }

  static boolean wasPublished(String relatedResource, Session session) throws RepositoryException {
    String relatedResourceJcrPath = BASE_NODE_PATH + relatedResource;
    return session.nodeExists(relatedResourceJcrPath);
  }
}
