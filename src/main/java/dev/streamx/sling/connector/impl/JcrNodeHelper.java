package dev.streamx.sling.connector.impl;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import org.apache.jackrabbit.commons.JcrUtils;

final class JcrNodeHelper {

  private JcrNodeHelper() {
    // no instances
  }

  static Node createNode(String path, Session session) throws RepositoryException {
    return JcrUtils.getOrCreateByPath(path, "nt:unstructured", session);
  }

  /**
   * Removes the specified JCR node and recursively deletes its parent nodes if they become orphaned (childless) as a result. <br />
   * The deletion proceeds upwards in the node hierarchy until a parent node has other children
   * or the specified {@code basePath} is reached. The node at {@code basePath} is also removed if it becomes empty.
   */
  static void removeNodeAlongWithOrphanedParents(Node nodeToRemove, String basePath) throws RepositoryException {
    nodeToRemove = removeNodeAndReturnParent(nodeToRemove);

    while (isInHierarchy(nodeToRemove, basePath) && !nodeToRemove.hasNodes()) {
      nodeToRemove = removeNodeAndReturnParent(nodeToRemove);
    }
  }

  private static boolean isInHierarchy(Node node, String basePath) throws RepositoryException {
    String nodePath = node.getPath();
    return nodePath.startsWith(basePath + "/") || nodePath.equals(basePath);
  }

  private static Node removeNodeAndReturnParent(Node nodeToRemove) throws RepositoryException {
    Node parentNode = nodeToRemove.getParent();
    nodeToRemove.remove();
    return parentNode;
  }
}
