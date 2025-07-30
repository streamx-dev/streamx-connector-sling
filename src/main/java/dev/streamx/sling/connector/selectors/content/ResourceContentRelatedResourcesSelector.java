package dev.streamx.sling.connector.selectors.content;

import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.util.SimpleInternalRequest;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.api.uri.SlingUriBuilder;
import org.apache.sling.engine.SlingRequestProcessor;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts paths of related resources from the content of a resource.
 */
@Component(
    service = {ResourceContentRelatedResourcesSelector.class, RelatedResourcesSelector.class},
    immediate = true,
    configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(
    ocd = ResourceContentRelatedResourcesSelectorConfig.class,
    factory = true
)
public class ResourceContentRelatedResourcesSelector implements RelatedResourcesSelector {

  private static final Logger LOG = LoggerFactory.getLogger(ResourceContentRelatedResourcesSelector.class);

  private final AtomicReference<ResourceContentRelatedResourcesSelectorConfig> config;
  private final SlingRequestProcessor slingRequestProcessor;
  private final ResourceResolverFactory resourceResolverFactory;

  private List<Pattern> relatedResourcePathIncludePatterns;
  private @Nullable Pattern relatedResourcePathExcludePattern;
  private @Nullable Pattern resourceRequiredPathRegex;
  private @Nullable Pattern resourceRequiredPrimaryNodeTypeRegex;
  private @Nullable Pattern relatedResourceProcessablePathPattern;

  /**
   * Constructs an instance of this class.
   *
   * @param config                  configuration for this service
   * @param slingRequestProcessor   {@link SlingRequestProcessor} to use when retrieving resource
   *                                content
   * @param resourceResolverFactory {@link ResourceResolverFactory} to use when accessing resources
   */
  @Activate
  public ResourceContentRelatedResourcesSelector(
      ResourceContentRelatedResourcesSelectorConfig config,
      @Reference SlingRequestProcessor slingRequestProcessor,
      @Reference ResourceResolverFactory resourceResolverFactory
  ) {
    this.config = new AtomicReference<>(config);
    this.slingRequestProcessor = slingRequestProcessor;
    this.resourceResolverFactory = resourceResolverFactory;
    loadPatterns();
  }

  private void loadPatterns() {
    ResourceContentRelatedResourcesSelectorConfig currentConfig = config.get();
    relatedResourcePathIncludePatterns = Arrays.stream(currentConfig.references_search$_$regexes())
        .map(Pattern::compile)
        .collect(Collectors.toUnmodifiableList());
    relatedResourcePathExcludePattern = compilePattern(currentConfig.references_exclude$_$from$_$result_regex());
    resourceRequiredPathRegex = compilePattern(currentConfig.resource_required$_$path_regex());
    resourceRequiredPrimaryNodeTypeRegex = compilePattern(currentConfig.resource_required$_$primary$_$node$_$type_regex());
    relatedResourceProcessablePathPattern = compilePattern(currentConfig.related$_$resource_processable$_$path_regex());
  }

  @Nullable
  private Pattern compilePattern(String regex) {
    if (regex == null) {
      return null;
    }
    return Pattern.compile(regex);
  }

  @Modified
  void configure(ResourceContentRelatedResourcesSelectorConfig config) {
    this.config.set(config);
    loadPatterns();
  }

  /**
   * Retrieves a collection of related resources based on the specified resource path.
   *
   * @param resourceInfo information about the resource for which related resources are to be selected
   * @return a collection of {@code ResourceInfo} objects that are related to the specified resource.
   *  Every returned {@code ResourceInfo} object has null value for its primaryNodeType field.
   */
  @Override
  public Collection<ResourceInfo> getRelatedResources(ResourceInfo resourceInfo) {
    String resourcePath = resourceInfo.getPath();
    LOG.debug("Getting related resources for '{}'", resourcePath);
    if (!matches(resourcePath, resourceRequiredPathRegex)) {
      return Collections.emptyList();
    }

    try (ResourceResolver resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null)) {
      return getRelatedResources(resourceInfo, resourceResolver);
    } catch (LoginException exception) {
      LOG.error("Failed to create Resource Resolver to load related resources for {}", resourcePath, exception);
      return Collections.emptyList();
    }
  }

  private List<ResourceInfo> getRelatedResources(ResourceInfo resource, ResourceResolver resourceResolver) {
    String primaryNodeType = resource.getProperty(JcrConstants.JCR_PRIMARYTYPE);
    if (primaryNodeType == null || !matches(primaryNodeType, resourceRequiredPrimaryNodeTypeRegex)) {
      return Collections.emptyList();
    }

    String resourcePath = resource.getPath();
    Set<String> extractedPaths = extractPathsOfRelatedResources(resourcePath, resourceResolver);
    for (String extractedPath : Set.copyOf(extractedPaths)) {
      extractPathsFromNestedRelatedResource(extractedPath, resourceResolver, extractedPaths);
    }

    LOG.info("Recognized paths for '{}': {}", resourcePath, extractedPaths);

    return extractedPaths.stream()
        .map(ResourceInfo::new)
        .collect(Collectors.toUnmodifiableList());
  }

  /**
   * Applies a set of configured regular expressions to the given resourcePath and
   * extracts all matching paths. For each regular expression, the first capturing group (group(1))
   * is used to identify a candidate path.
   * <p>
   * All extracted path strings are then de-duplicated
   * and sorted. If no matches are found, an empty {@link Set} is returned.
   * </p>
   *
   * @param resourcePath path to text resource from which to extract paths
   * @return {@link Set} of unique resource paths; may be empty if no matches
   * are found
   */
  private Set<String> extractPathsOfRelatedResources(String resourcePath, ResourceResolver resourceResolver) {
    String resourceContent = readResourceContent(resourcePath + getResourcePathPostfixToAppend(), resourceResolver);
    return extractMatchingRelatedResourcePaths(resourcePath, resourceContent);
  }

  private void extractPathsFromNestedRelatedResource(String resourcePath, ResourceResolver resourceResolver, Set<String> extractedPaths) {
    if (!matches(resourcePath, relatedResourceProcessablePathPattern)) {
      return;
    }
    String resourceAsString = readResourceContent(resourcePath, resourceResolver);
    Set<String> nestedRelatedResourcePaths = extractMatchingRelatedResourcePaths(resourcePath, resourceAsString);
    for (String nestedRelatedResourcePath : nestedRelatedResourcePaths) {
      if (!extractedPaths.contains(nestedRelatedResourcePath)) { // avoid circular references
        extractedPaths.add(nestedRelatedResourcePath);
        extractPathsFromNestedRelatedResource(nestedRelatedResourcePath, resourceResolver, extractedPaths);
      }
    }
  }

  private Set<String> extractMatchingRelatedResourcePaths(String resourcePath, String resourceContent) {
    Set<String> matchingPaths = new TreeSet<>();
    for (Pattern includePattern : relatedResourcePathIncludePatterns) {
      Matcher matcher = includePattern.matcher(resourceContent);
      while (matcher.find()) {
        if (matcher.groupCount() > 0) {
          String relatedResourcePath = matcher.group(1);
          if (isRelatedResourcePathValidForCollecting(relatedResourcePath)) {
            String normalizedPath = normalizePath(resourcePath, relatedResourcePath);
            matchingPaths.add(normalizedPath);
          }
        }
      }
    }
    return matchingPaths;
  }

  private boolean isRelatedResourcePathValidForCollecting(String relatedResourcePath) {
    return !matches(relatedResourcePath, relatedResourcePathExcludePattern)
           && !isExternalUrl(relatedResourcePath);
  }

  private String readResourceContent(String resourcePath, ResourceResolver resourceResolver) {
    SlingUri slingUri = SlingUriBuilder.parse(resourcePath, resourceResolver).build();
    return new SimpleInternalRequest(slingUri, slingRequestProcessor, resourceResolver).getResponseAsString();
  }

  private String getResourcePathPostfixToAppend() {
    return Optional
        .ofNullable(config.get().resource$_$path_postfix$_$to$_$append())
        .orElse(StringUtils.EMPTY);
  }

  private static String normalizePath(String parentPath, String childPath) {
    if (childPath.startsWith("/")) {
      return childPath;
    }
    return Paths.get(parentPath).getParent()
        .resolve(childPath)
        .normalize()
        .toString()
        .replace('\\', '/');
  }

  private static boolean isExternalUrl(String input) {
    if (input.startsWith("//")) {
      return true; // protocol-relative URL
    }
    try {
      URI uri = new URI(input);
      return uri.getScheme() != null;
    } catch (URISyntaxException e) {
      return false; // invalid URI → assume it is a local path
    }
  }

  private static boolean matches(String stringToTest, @Nullable Pattern pattern) {
    if (pattern == null) {
      return false;
    }
    return pattern.matcher(stringToTest).matches();
  }
}
