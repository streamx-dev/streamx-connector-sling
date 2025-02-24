package dev.streamx.sling.connector.paths;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.RelatedResource;
import dev.streamx.sling.connector.RelatedResourcesSelector;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

@Component(
    service = {PathsExtraction.class, RelatedResourcesSelector.class},
    immediate = true,
    configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(
    ocd = PathsExtractionConfig.class,
    factory = true
)
public class PathsExtraction implements RelatedResourcesSelector {

  private static final Logger LOG = LoggerFactory.getLogger(PathsExtraction.class);
  private final AtomicReference<PathsExtractionConfig> config;
  private final SlingRequestProcessor slingRequestProcessor;
  private final ResourceResolverFactory resourceResolverFactory;

  @Activate
  public PathsExtraction(
      PathsExtractionConfig config,
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
  void configure(PathsExtractionConfig config) {
    this.config.set(config);
  }

  @Override
  public Collection<RelatedResource> getRelatedResources(
      String resourcePath, PublicationAction action
  ) {
    ResourcePath resourcePathWrapped = new ResourcePath(resourcePath);
    LOG.debug("Getting related resources for '{}'", resourcePathWrapped);
    ResourceFilter resourceFilter = new ResourceFilter(
        config.get(), slingRequestProcessor, resourceResolverFactory
    );
    boolean isAcceptableResource = resourceFilter.isAcceptable(resourcePathWrapped);
    LOG.trace(
        "Is resource at path '{}' acceptable? Answer: {}", resourcePath, isAcceptableResource
    );
    if (!isAcceptableResource) {
      return List.of();
    } else {
      Collection<ResourcePath> extractedPaths = extract(
          new PathToTextResource(resourcePathWrapped)
      );
      if (LOG.isInfoEnabled()) {
        LOG.info(
            "Recognized paths for '{}': {}", resourcePathWrapped,
            extractedPaths.stream()
                          .map(ResourcePath::get)
                          .sorted()
                          .collect(Collectors.toList())
        );
      }
      return extractedPaths.stream()
          .map(ResourcePath::get)
          .map(recognizedPath -> new RelatedResource(recognizedPath, action))
          .collect(Collectors.toUnmodifiableList());
    }
  }

  /**
   * Applies a set of configured regular expressions to the given {@link PathToTextResource} and
   * extracts all matching paths. For each regular expression:
   * <ul>
   *   <li>The first capturing group (group(1)) is used to identify a candidate path.</li>
   *   <li>If the candidate path contains comma-separated entries, each entry is split, trimmed,
   *       and only its first whitespace-delimited token is taken.</li>
   * </ul>
   *
   * <p>
   * All extracted path strings are then converted into {@link ResourcePath} objects, de-duplicated,
   * and sorted. If no matches are found, an empty {@link Collection} is returned.
   * </p>
   *
   * @param pathToTextResource {@link PathToTextResource} from which to extract paths
   * @return {@link Collection} of unique {@link ResourcePath} objects;
   *         may be empty if no matches are found
   */
  private Collection<ResourcePath> extract(PathToTextResource pathToTextResource) {
    List<Pattern> patterns = Stream.of(config.get().search_regexes())
        .map(Pattern::compile)
        .collect(Collectors.toUnmodifiableList());
    LOG.trace("Recognizing paths for '{}' with these patterns: {}", pathToTextResource, patterns);

    String resourceAsString = asString(pathToTextResource);

    Collection<ResourcePath> extractedPaths = patterns.stream()
        .flatMap(
            pattern -> {
              Matcher matcher = pattern.matcher(resourceAsString);
              return Stream.iterate(matcher, Matcher::find, nextMatcher -> nextMatcher)
                  .map(mappingMatcher -> mappingMatcher.group(NumberUtils.INTEGER_ONE));
            }
        ).flatMap(
            matchedPath -> matchedPath.contains(",")
                ? Stream.of(matchedPath.split(","))
                .map(String::trim)
                .map(part -> part.split("\\s+")[NumberUtils.INTEGER_ZERO])
                : Stream.of(matchedPath)
        ).sorted()
        .filter(path -> path.startsWith("/"))
        .distinct()
        .filter(path -> !path.matches(config.get().exclude$_$from$_$result_regex()))
        .map(ResourcePath::new)
        .collect(Collectors.toUnmodifiableList());
    LOG.debug("From '{}' these paths were extracted: {}", pathToTextResource, extractedPaths);
    return extractedPaths;
  }

  @SuppressWarnings({"squid:S1874", "deprecation"})
  private String asString(PathToTextResource pathToTextResource) {
    try (
        ResourceResolver resourceResolver
            = resourceResolverFactory.getAdministrativeResourceResolver(null)
    ) {
      String resourcePathUnwrapped = pathToTextResource.get();
      return Optional.ofNullable(resourceResolver.getResource(resourcePathUnwrapped))
          .map(
              resource -> new InternalRequestForResource(
                  resource, slingRequestProcessor, config.get().extension$_$to$_$append()
              )
          ).map(InternalRequestForResource::getResponseAsString)
          .orElse(StringUtils.EMPTY);
    } catch (LoginException exception) {
      String message = String.format("Unable to recognize paths for %s", pathToTextResource);
      LOG.error(message, exception);
      return StringUtils.EMPTY;
    }
  }
}
