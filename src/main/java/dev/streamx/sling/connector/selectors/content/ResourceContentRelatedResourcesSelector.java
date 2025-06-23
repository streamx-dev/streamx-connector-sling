package dev.streamx.sling.connector.selectors.content;

import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.util.SimpleInternalRequest;
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
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.api.uri.SlingUriBuilder;
import org.apache.sling.engine.SlingRequestProcessor;
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
  private Pattern relatedResourcePathExcludePattern;
  private Pattern resourceRequiredPathRegex;
  private Pattern resourceRequiredPrimaryNodeTypeRegex;
  private Pattern relatedResourceProcessablePathPattern;

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
    relatedResourcePathExcludePattern = Pattern.compile(currentConfig.references_exclude$_$from$_$result_regex());
    resourceRequiredPathRegex = Pattern.compile(currentConfig.resource_required$_$path_regex());
    resourceRequiredPrimaryNodeTypeRegex = Pattern.compile(currentConfig.resource_required$_$primary$_$node$_$type_regex());
    relatedResourceProcessablePathPattern = Pattern.compile(currentConfig.related$_$resource_processable$_$path_regex());
  }

  @Modified
  void configure(ResourceContentRelatedResourcesSelectorConfig config) {
    this.config.set(config);
    loadPatterns();
  }

  /**
   * Retrieves a collection of related resources based on the specified resource path.
   *
   * @param resourcePath the path of the resource for which related resources are to be selected
   * @return a collection of {@code ResourceInfo} objects that are related to the specified resource.
   *  Every returned {@code ResourceInfo} object has null value for its primaryNodeType field.
   */
  @Override
  public Collection<ResourceInfo> getRelatedResources(String resourcePath) {
    LOG.debug("Getting related resources for '{}'", resourcePath);
    if (!ResourceFilter.isAcceptableResourcePath(resourcePath, resourceRequiredPathRegex)) {
      return Collections.emptyList();
    }

    try (ResourceResolver resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null)) {
      return getRelatedResources(resourcePath, resourceResolver);
    } catch (LoginException exception) {
      LOG.error("Failed to create Resource Resolver to load related resources for {}", resourcePath, exception);
      return Collections.emptyList();
    }
  }

  private List<ResourceInfo> getRelatedResources(String resourcePath, ResourceResolver resourceResolver) {
    if (!ResourceFilter.isAcceptablePrimaryNodeType(resourcePath, resourceResolver, resourceRequiredPrimaryNodeTypeRegex)) {
      return Collections.emptyList();
    }

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
    String resourceAsString = readResourceAsString(resourcePath + getResourcePathPostfixToAppend(), resourceResolver);
    return extractMatchingRelatedResourcePaths(resourceAsString);
  }

  private void extractPathsFromNestedRelatedResource(String resourcePath, ResourceResolver resourceResolver, Set<String> extractedPaths) {
    if (!relatedResourceProcessablePathPattern.matcher(resourcePath).matches()) {
      return;
    }
    String resourceAsString = readResourceAsString(resourcePath, resourceResolver);
    Set<String> nestedRelatedResourcePaths = extractMatchingRelatedResourcePaths(resourceAsString);
    for (String nestedRelatedResourcePath : nestedRelatedResourcePaths) {
      if (!extractedPaths.contains(nestedRelatedResourcePath)) { // avoid circular references
        extractedPaths.add(nestedRelatedResourcePath);
        extractPathsFromNestedRelatedResource(nestedRelatedResourcePath, resourceResolver, extractedPaths);
      }
    }
  }


  private Set<String> extractMatchingRelatedResourcePaths(String mainResourceContent) {
    Set<String> matchingPaths = new TreeSet<>();
    for (Pattern includePattern : relatedResourcePathIncludePatterns) {
      Matcher matcher = includePattern.matcher(mainResourceContent);
      while (matcher.find()) {
        if (matcher.groupCount() > 0) {
          String relatedResourcePath = matcher.group(1);
          if (!relatedResourcePathExcludePattern.matcher(relatedResourcePath).matches()) {
            matchingPaths.add(relatedResourcePath);
          }
        }
      }
    }
    return matchingPaths;
  }

  private String readResourceAsString(String resourcePath, ResourceResolver resourceResolver) {
    SlingUri slingUri = SlingUriBuilder.parse(resourcePath, resourceResolver).build();
    return new SimpleInternalRequest(slingUri, slingRequestProcessor, resourceResolver).getResponseAsString();
  }

  private String getResourcePathPostfixToAppend() {
    return Optional
        .ofNullable(config.get().resource$_$path_postfix$_$to$_$append())
        .orElse(StringUtils.EMPTY);
  }
}
