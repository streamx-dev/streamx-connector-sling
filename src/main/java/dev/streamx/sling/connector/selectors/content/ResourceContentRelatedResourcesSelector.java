package dev.streamx.sling.connector.selectors.content;

import dev.streamx.sling.connector.RelatedResource;
import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.util.SimpleInternalRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
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
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts {@link RelatedResource}s from the content of a resource.
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

  private static final Logger LOG = LoggerFactory.getLogger(
      ResourceContentRelatedResourcesSelector.class
  );
  private final AtomicReference<ResourceContentRelatedResourcesSelectorConfig> config;
  private final SlingRequestProcessor slingRequestProcessor;
  private final ResourceResolverFactory resourceResolverFactory;

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
      @Reference(cardinality = ReferenceCardinality.MANDATORY)
      SlingRequestProcessor slingRequestProcessor,
      @Reference(cardinality = ReferenceCardinality.MANDATORY)
      ResourceResolverFactory resourceResolverFactory
  ) {
    this.config = new AtomicReference<>(config);
    this.slingRequestProcessor = slingRequestProcessor;
    this.resourceResolverFactory = resourceResolverFactory;
  }

  @Modified
  void configure(ResourceContentRelatedResourcesSelectorConfig config) {
    this.config.set(config);
  }

  @Override
  public Collection<RelatedResource> getRelatedResources(String resourcePath) {
    LOG.debug("Getting related resources for '{}'", resourcePath);
    try (ResourceResolver resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null)) {
      return getRelatedResources(resourcePath, resourceResolver);
    } catch (LoginException exception) {
      LOG.error("Failed to create Resource Resolver to load related resources for {}", resourcePath, exception);
      return Collections.emptyList();
    }
  }

  private List<RelatedResource> getRelatedResources(
      String resourcePath, ResourceResolver resourceResolver
  ) {
    ResourceFilter resourceFilter = new ResourceFilter(config.get(), resourceResolver);
    boolean isAcceptableResource = resourceFilter.isAcceptable(resourcePath);
    LOG.trace(
        "Is resource at path '{}' acceptable? Answer: {}", resourcePath, isAcceptableResource
    );
    if (!isAcceptableResource) {
      return Collections.emptyList();
    }

    Collection<String> extractedPaths = extract(resourcePath, resourceResolver);
    if (LOG.isInfoEnabled()) {
      LOG.info(
          "Recognized paths for '{}': {}", resourcePath,
          extractedPaths.stream()
              .sorted()
              .collect(Collectors.toList())
      );
    }

    return extractedPaths.stream()
        .map(recognizedPath -> new RelatedResource(
            recognizedPath,
            resourceFilter.extractPrimaryNodeType(recognizedPath, resourceResolver)
        ))
        .collect(Collectors.toUnmodifiableList());
  }

  /**
   * Applies a set of configured regular expressions to the given resourcePath and
   * extracts all matching paths. For each regular expression, the first capturing group (group(1))
   * is used to identify a candidate path.
   * <p>
   * All extracted path strings are then de-duplicated
   * and sorted. If no matches are found, an empty {@link Collection} is returned.
   * </p>
   *
   * @param resourcePath path to text resource from which to extract paths
   * @return {@link Collection} of unique resource paths; may be empty if no matches
   * are found
   */
  private Collection<String> extract(String resourcePath, ResourceResolver resourceResolver) {
    List<Pattern> patterns = Stream.of(config.get().references_search$_$regexes())
        .map(Pattern::compile)
        .collect(Collectors.toUnmodifiableList());
    LOG.trace("Recognizing paths for '{}' with these patterns: {}", resourcePath, patterns);

    String resourceAsString = readResourceAsString(resourcePath, resourceResolver);

    Collection<String> extractedPaths = patterns.stream()
        .flatMap(
            pattern -> {
              Matcher matcher = pattern.matcher(resourceAsString);
              return Stream.iterate(matcher, Matcher::find, nextMatcher -> nextMatcher)
                  .map(mappingMatcher -> mappingMatcher.group(NumberUtils.INTEGER_ONE));
            }
        )
        .filter(path -> !path.matches(config.get().references_exclude$_$from$_$result_regex()))
        .collect(Collectors.toCollection(TreeSet::new));
    LOG.debug("From '{}' these paths were extracted: {}", resourcePath, extractedPaths);
    return extractedPaths;
  }

  private String readResourceAsString(String resourcePath, ResourceResolver resourceResolver) {
    String rawUri = String.format(
        "%s%s", resourcePath,
        Optional.ofNullable(config.get().resource$_$path_postfix$_$to$_$append())
            .orElse(StringUtils.EMPTY)
    );
    SlingUri slingUri = SlingUriBuilder.parse(rawUri, resourceResolver).build();
    return new SimpleInternalRequest(
        slingUri, slingRequestProcessor, resourceResolver
    ).getResponseAsString();
  }
}
