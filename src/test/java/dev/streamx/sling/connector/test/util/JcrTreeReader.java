package dev.streamx.sling.connector.test.util;

import static java.util.Objects.requireNonNull;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;

public class JcrTreeReader {

  private JcrTreeReader() {
    // no instances
  }

  /**
   * Returns map of all nodes under the given parent node (and the parent node itself), in the order of iterating the tree.
   * Every map entry contains the node path as the key, and map of its properties as value
   */
  public static Map<String, Map<String, Set<String>>> getNestedNodes(String parentNodePath, ResourceResolver resourceResolver) throws RepositoryException {
    Map<String, Map<String, Set<String>>> result = new LinkedHashMap<>();

    Session session = requireNonNull(resourceResolver.adaptTo(Session.class));
    Node parentNode = session.getNode(parentNodePath);
    readNode(parentNode, result);

    return result;
  }

  private static void readNode(Node node, Map<String, Map<String, Set<String>>> result) throws RepositoryException {
    Map<String, Set<String>> nodeProperties = readNodeProperties(node);
    result.put(node.getPath(), nodeProperties);

    NodeIterator childNodes = node.getNodes();
    while (childNodes.hasNext()) {
      readNode(childNodes.nextNode(), result);
    }
  }

  private static Map<String, Set<String>> readNodeProperties(Node node) throws RepositoryException {
    Map<String, Set<String>> nodeProperties = new LinkedHashMap<>();

    PropertyIterator properties = node.getProperties();
    while (properties.hasNext()) {
      Property property = properties.nextProperty();
      String propertyName = property.getName();
      if (!StringUtils.equalsAny(propertyName, "jcr:created", "jcr:createdBy", "jcr:primaryType")) {
        Set<String> propertyValues = readPropertyValues(property);
        nodeProperties.put(propertyName, propertyValues);
      }
    }

    return nodeProperties;
  }

  private static Set<String> readPropertyValues(Property property) throws RepositoryException {
    if (!property.isMultiple()) {
      return Set.of(property.getString());
    }
    Set<String> propertyValues = new LinkedHashSet<>();
    for (Value value : property.getValues()) {
      propertyValues.add(value.getString());
    }
    return propertyValues;
  }

}
