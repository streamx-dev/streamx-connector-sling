package dev.streamx.sling.connector.selectors.content;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ResourceFilter {

  private static final Logger LOG = LoggerFactory.getLogger(ResourceFilter.class);

  public static boolean isAcceptableResourcePath(String resourcePath,
                                    ResourceContentRelatedResourcesSelectorConfig config) {
    return resourcePath.matches(config.resource_required$_$path_regex());
  }

  public static boolean isAcceptablePrimaryNodeType(String resourcePath, ResourceResolver resourceResolver,
                                     ResourceContentRelatedResourcesSelectorConfig config) {
    String actualPrimaryNodeType = extractPrimaryNodeType(resourcePath, resourceResolver);
    if (actualPrimaryNodeType == null) {
      return false;
    }
    return actualPrimaryNodeType.matches(config.resource_required$_$primary$_$node$_$type_regex());
  }

  private static String extractPrimaryNodeType(String resourcePath, ResourceResolver resourceResolver) {
    try {
      Resource resource = resourceResolver.resolve(resourcePath);
      Node node = resource.adaptTo(Node.class);
      if (node != null) {
        return node.getPrimaryNodeType().getName();
      }
    } catch (RepositoryException exception) {
      LOG.error("Failed to extract primary node type from {}", resourcePath, exception);
    }
    return null;
  }

}
