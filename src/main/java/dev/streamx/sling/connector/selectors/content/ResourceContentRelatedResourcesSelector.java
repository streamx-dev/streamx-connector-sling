package dev.streamx.sling.connector.selectors.content;

import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.util.SimpleInternalRequest;
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
 * Extracts {@link ResourceInfo}s from the content of a resource.
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
  }

  @Modified
  void configure(ResourceContentRelatedResourcesSelectorConfig config) {
    this.config.set(config);
  }

  @Override
  public Collection<ResourceInfo> getRelatedResources(String resourcePath) {
    LOG.debug("Getting related resources for '{}'", resourcePath);
    if (!ResourceFilter.isAcceptableResourcePath(resourcePath, config.get())) {
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
    if (!ResourceFilter.isAcceptablePrimaryNodeType(resourcePath, resourceResolver, config.get())) {
      return Collections.emptyList();
    }

    Set<String> extractedPaths = extract(resourcePath, resourceResolver);
    LOG.info("Recognized paths for '{}': {}", resourcePath, extractedPaths);

    return extractedPaths.stream()
        .map(recognizedPath -> new ResourceInfo(
            recognizedPath,
            ResourceFilter.extractPrimaryNodeType(recognizedPath, resourceResolver)
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
  private Set<String> extract(String resourcePath, ResourceResolver resourceResolver) {
    String[] includeRegexes = config.get().references_search$_$regexes();
    String excludeRegex = config.get().references_exclude$_$from$_$result_regex();

    String resourceAsString = readResourceAsString(resourcePath, resourceResolver);
    Set<String> resultPaths = new TreeSet<>();

    for (String regex : includeRegexes) {
      Matcher matcher = Pattern.compile(regex).matcher(resourceAsString);
      while (matcher.find()) {
        if (matcher.groupCount() > 0) {
          String path = matcher.group(1);
          if (!path.matches(excludeRegex)) {
            resultPaths.add(path);
          }
        }
      }
    }

    return resultPaths;
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
