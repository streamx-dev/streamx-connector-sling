package dev.streamx.sling.connector.paths;

import java.util.Optional;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.api.uri.SlingUriBuilder;
import org.apache.sling.engine.SlingRequestProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ResourceFilter {

  private static final Logger LOG = LoggerFactory.getLogger(ResourceFilter.class);
  private final PathsExtractionConfig pathsExtractionConfig;
  private final SlingRequestProcessor slingRequestProcessor;
  private final ResourceResolverFactory resourceResolverFactory;

  ResourceFilter(
      PathsExtractionConfig pathsExtractionConfig,
      SlingRequestProcessor slingRequestProcessor,
      ResourceResolverFactory resourceResolverFactory
  ) {
    this.pathsExtractionConfig = pathsExtractionConfig;
    this.slingRequestProcessor = slingRequestProcessor;
    this.resourceResolverFactory = resourceResolverFactory;
  }

  boolean isAcceptable(ResourcePath resourcePath) {
    return matchesPath(resourcePath)
        && matchesExtension(resourcePath)
        && matchesContentType(resourcePath)
        && matchesPrimaryNT(resourcePath);
  }

  @SuppressWarnings({"squid:S1874", "deprecation"})
  private boolean matchesPrimaryNT(ResourcePath resourcePath) {
    try (
        ResourceResolver resourceResolver
            = resourceResolverFactory.getAdministrativeResourceResolver(null)
    ) {
      String requiredPrimaryNTRegex
          = pathsExtractionConfig.required$_$primary$_$node$_$type_regex();
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

  @SuppressWarnings({"squid:S1874", "deprecation"})
  private boolean matchesExtension(ResourcePath resourcePath) {
    try (
        ResourceResolver resourceResolver
            = resourceResolverFactory.getAdministrativeResourceResolver(null)
    ) {
      String requiredExtensionRegex = pathsExtractionConfig.required$_$extension_regex();
      String resourcePathUnwrapped = resourcePath.get();
      SlingUri slingURI = SlingUriBuilder.parse(resourcePathUnwrapped, resourceResolver).build();
      String recognizedExtension = Optional.ofNullable(slingURI.getExtension())
          .orElse(StringUtils.EMPTY);
      boolean doesMatchExtension = recognizedExtension.matches(requiredExtensionRegex);
      LOG.trace(
          "Does resource at path '{}' with '{}' extension recognized match this extension regex: "
        + "'{}'? Answer: {}",
          resourcePath, recognizedExtension, requiredExtensionRegex, doesMatchExtension
      );
      return doesMatchExtension;
    } catch (LoginException exception) {
      String message = String.format("Failed to verify extension for '%s'", resourcePath);
      LOG.error(message, exception);
      return false;
    }
  }

  private boolean matchesPath(ResourcePath resourcePath) {
    String requiredPathRegex = pathsExtractionConfig.required$_$path_regex();
    boolean doesMatchPath = resourcePath.matches(requiredPathRegex);
    LOG.trace(
        "Does resource at path '{}' match this path regex: '{}'? Answer: {}",
        resourcePath, requiredPathRegex, doesMatchPath
    );
    return doesMatchPath;
  }

  @SuppressWarnings({"squid:S1874", "deprecation"})
  private boolean matchesContentType(ResourcePath resourcePath) {
    try (
        ResourceResolver resourceResolver
            = resourceResolverFactory.getAdministrativeResourceResolver(null)
    ) {
      String requiredContentTypeRegex = pathsExtractionConfig.required$_$content$_$type_regex();
      String resourcePathUnwrapped = resourcePath.get();
      boolean doesMatchContentType = Optional.ofNullable(
              resourceResolver.getResource(resourcePathUnwrapped)
          ).map(
              resource -> new InternalRequestForResource(
                  resource, slingRequestProcessor, pathsExtractionConfig.extension$_$to$_$append()
              )
          ).map(InternalRequestForResource::contentType)
          .stream()
          .anyMatch(contentType -> contentType.matches(requiredContentTypeRegex));
      LOG.trace(
          "Does resource at path '{}' match this content type regex: '{}'? Answer: {}",
          resourcePath, requiredContentTypeRegex, doesMatchContentType
      );
      return doesMatchContentType;
    } catch (LoginException exception) {
      String message = String.format(
          "Failed to verify content type for '%s'", resourcePath
      );
      LOG.error(message, exception);
      return false;
    }
  }
}
