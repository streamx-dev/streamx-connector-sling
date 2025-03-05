package dev.streamx.sling.connector.selectors.content;

import java.util.Optional;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ResourceFilter {

  private static final Logger LOG = LoggerFactory.getLogger(ResourceFilter.class);
  private final ResourceContentRelatedResourcesSelectorConfig config;
  private final ResourceResolverFactory resourceResolverFactory;

  ResourceFilter(
      ResourceContentRelatedResourcesSelectorConfig config,
      ResourceResolverFactory resourceResolverFactory
  ) {
    this.config = config;
    this.resourceResolverFactory = resourceResolverFactory;
  }

  boolean isAcceptable(ResourcePath resourcePath) {
    return matchesPath(resourcePath)
        && matchesPrimaryNT(resourcePath);
  }

  @SuppressWarnings({"squid:S1874", "deprecation"})
  private boolean matchesPrimaryNT(ResourcePath resourcePath) {
    try (
        ResourceResolver resourceResolver
            = resourceResolverFactory.getAdministrativeResourceResolver(null)
    ) {
      String requiredPrimaryNTRegex
          = config.resource_required$_$primary$_$node$_$type_regex();
      String resourcePathUnwrapped = resourcePath.get();
      boolean doesMatchPrimaryNT = Optional.ofNullable(
              resourceResolver.getResource(resourcePathUnwrapped)
          ).map(resource -> resource.adaptTo(Node.class))
          .map(this::extractPrimaryNT)
          .orElse(StringUtils.EMPTY)
          .matches(requiredPrimaryNTRegex);
      LOG.trace(
          "Does resource at path '{}' match this primary node type regex: '{}'? Answer: {}",
          resourcePath, requiredPrimaryNTRegex, doesMatchPrimaryNT
      );
      return doesMatchPrimaryNT;
    } catch (LoginException exception) {
      String message = String.format("Failed to verify primary node type for '%s'", resourcePath);
      LOG.error(message, exception);
      return false;
    }
  }

  private String extractPrimaryNT(Node node) {
    try {
      NodeType primaryNT = node.getPrimaryNodeType();
      return primaryNT.getName();
    } catch (RepositoryException exception) {
      String message = String.format("Failed to extract primary node type for '%s'", node);
      LOG.error(message, exception);
      return StringUtils.EMPTY;
    }
  }

  private boolean matchesPath(ResourcePath resourcePath) {
    String requiredPathRegex = config.resource_required$_$path_regex();
    boolean doesMatchPath = resourcePath.matches(requiredPathRegex);
    LOG.trace(
        "Does resource at path '{}' match this path regex: '{}'? Answer: {}",
        resourcePath, requiredPathRegex, doesMatchPath
    );
    return doesMatchPath;
  }
}
