package dev.streamx.sling.connector.util;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.api.uri.SlingUriBuilder;

/**
 * Creates a {@link SlingUri} from a raw Sling URI string.
 */
public class DefaultSlingUriBuilder {

  private final String rawSlingUri;
  private final ResourceResolverFactory resourceResolverFactory;

  /**
   * Constructs a new instance of this class.
   *
   * @param rawSlingUri             the raw Sling URI string
   * @param resourceResolverFactory {@link ResourceResolverFactory} instance that will be used to
   *                                access Sling Resources
   */
  public DefaultSlingUriBuilder(
      String rawSlingUri, ResourceResolverFactory resourceResolverFactory
  ) {
    this.rawSlingUri = rawSlingUri;
    this.resourceResolverFactory = resourceResolverFactory;
  }

  /**
   * Returns a {@link SlingUri} built out of the raw {@link String}.
   *
   * @return {@link SlingUri} built out of the raw {@link String}
   */
  @SuppressWarnings({"squid:1874", "deprecation"})
  public SlingUri build() {
    try (
        ResourceResolver resourceResolver
            = resourceResolverFactory.getAdministrativeResourceResolver(null)
    ) {
      return SlingUriBuilder.parse(rawSlingUri, resourceResolver).build();
    } catch (LoginException exception) {
      String message = String.format("Unable to build Sling URI from %s", rawSlingUri);
      throw new IllegalArgumentException(message, exception);
    }
  }
}
