package dev.streamx.sling.connector.selectors.content;

import java.util.Objects;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ResourceFilter {

  private static final Logger LOG = LoggerFactory.getLogger(ResourceFilter.class);
  private final ResourceContentRelatedResourcesSelectorConfig config;
  private final ResourceResolver resourceResolver;

  ResourceFilter(
      ResourceContentRelatedResourcesSelectorConfig config,
      ResourceResolver resourceResolver
  ) {
    this.config = config;
    this.resourceResolver = resourceResolver;
  }

  boolean isAcceptable(String resourcePath) {
    return matchesPath(resourcePath)
        && matchesPrimaryNT(resourcePath);
  }

  private boolean matchesPrimaryNT(String resourcePath) {
    String requiredPrimaryNTRegex = config.resource_required$_$primary$_$node$_$type_regex();
    String actualResourcePrimaryNT = extractPrimaryNodeType(resourcePath, resourceResolver);
    boolean doesMatchPrimaryNT = Objects.equals(actualResourcePrimaryNT, requiredPrimaryNTRegex);
    LOG.trace(
        "Does resource at path '{}' match this primary node type regex: '{}'? Answer: {}",
        resourcePath, requiredPrimaryNTRegex, doesMatchPrimaryNT
    );
    return doesMatchPrimaryNT;
  }

  @Nullable
  public String extractPrimaryNodeType(String resourcePath, ResourceResolver resourceResolver) {
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

  private boolean matchesPath(String resourcePath) {
    String requiredPathRegex = config.resource_required$_$path_regex();
    boolean doesMatchPath = resourcePath.matches(requiredPathRegex);
    LOG.trace(
        "Does resource at path '{}' match this path regex: '{}'? Answer: {}",
        resourcePath, requiredPathRegex, doesMatchPath
    );
    return doesMatchPath;
  }
}
