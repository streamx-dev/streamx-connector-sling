package dev.streamx.sling.connector.util;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.api.uri.SlingUriBuilder;

/**
 * Creates a {@link SlingUri} out of a {@link String}.
 */
public class DefaultSlingUriBuilder {

  private final String rawSlingUri;
  private final ResourceResolverFactory resourceResolverFactory;

  /**
   * Constructs a new instance of this class.
   *
   * @param rawSlingUri             {@link String} out of which the {@link SlingUri} should be
   *                                built; must be a valid {@link String} representation of a
   *                                {@link SlingUri}
   * @param resourceResolverFactory {@link ResourceResolverFactory} that will be used to access
   *                                Sling Resources
   */
  public DefaultSlingUriBuilder(
      String rawSlingUri, ResourceResolverFactory resourceResolverFactory
  ) {
    this.rawSlingUri = rawSlingUri;
    this.resourceResolverFactory = resourceResolverFactory;
  }

  /**
   * Returns a {@link SlingUri} built out of a {@link String}.
   *
   * @return {@link SlingUri} built out of a {@link String}
   */
  @SuppressWarnings({"squid:1874", "deprecation"})
  public SlingUri build() {
    try (
        ResourceResolver resourceResolver
            = resourceResolverFactory.getAdministrativeResourceResolver(null)
    ) {
      return SlingUriBuilder.parse(rawSlingUri, resourceResolver).build();
    } catch (LoginException exception) {
      String message = String.format("Unable to build %s from %s", SlingUri.class, rawSlingUri);
      throw new IllegalArgumentException(message, exception);
    }
  }
}
