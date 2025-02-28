package dev.streamx.sling.connector.handling;

import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.SimpleInternalRequest;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.UnpublishData;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.api.uri.SlingUriBuilder;
import org.apache.sling.engine.SlingRequestProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link PublicationHandler} for resources that are retrieved via internal requests.
 *
 * @param <T> type of the model to be ingested by StreamX
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class InternalResourcesHandler<T> implements PublicationHandler<T> {

  private static final Logger LOG = LoggerFactory.getLogger(InternalResourcesHandler.class);
  private final ResourceResolverFactory resourceResolverFactory;
  private final SlingRequestProcessor slingRequestProcessor;

  protected InternalResourcesHandler(
      ResourceResolverFactory resourceResolverFactory,
      SlingRequestProcessor slingRequestProcessor
  ) {
    this.resourceResolverFactory = resourceResolverFactory;
    this.slingRequestProcessor = slingRequestProcessor;
  }

  @Override
  public boolean canHandle(String resourcePath) {
    if (configuration().isEnabled()) {
      String resourcePathRegex = configuration().resourcePathRegex();
      boolean matches = resourcePath.matches(resourcePathRegex);
      LOG.trace(
          "Does resource path '{}' match this regex: '{}'? Answer: {}",
          resourcePath, resourcePathRegex, matches
      );
      return matches;
    } else {
      LOG.trace("Handler is disabled. Not handling '{}'", resourcePath);
      return false;
    }
  }

  @Override
  @SuppressWarnings({"squid:S1874", "deprecation"})
  public PublishData<T> getPublishData(String resourcePath) throws StreamxPublicationException {
    LOG.trace("Getting publish data for '{}'", resourcePath);
    try (
        ResourceResolver resourceResolver
            = resourceResolverFactory.getAdministrativeResourceResolver(null)
    ) {
      return generatePublishData(resourcePath, resourceResolver)
          .orElseThrow(
              () -> new StreamxPublicationException(
                  String.format("Failed to generate publish data for %s", resourcePath)
              )
          );
    } catch (LoginException exception) {
      throw new StreamxPublicationException(
          String.format("Failed to generate publish data for '%s'", resourcePath), exception
      );
    }
  }

  private Optional<PublishData<T>> generatePublishData(
      String resourcePath, ResourceResolver resourceResolver
  ) {
    SlingUri slingUri = SlingUriBuilder.parse(resourcePath, resourceResolver).build();
    SimpleInternalRequest simpleInternalRequest = new SimpleInternalRequest(
        slingUri, slingRequestProcessor, resourceResolver
    );
    try (InputStream inputStream = simpleInternalRequest.getResponseAsInputStream().orElseThrow()) {
      String channel = configuration().channel();
      Class<T> modelCLass = modelClass();
      T model = model(inputStream);
      PublishData<T> publishData = new PublishData<>(
          resourcePath, channel, modelCLass, model
      );
      LOG.trace("Generated {} for {}", publishData, resourcePath);
      return Optional.of(publishData);
    } catch (IOException exception) {
      String message = String.format("Failed to generate publish data for %s", resourcePath);
      LOG.error(message, exception);
      return Optional.empty();
    }
  }

  @Override
  public UnpublishData<T> getUnpublishData(String resourcePath) {
    String channel = configuration().channel();
    Class<T> modelClass = modelClass();
    return new UnpublishData<>(resourcePath, channel, modelClass);
  }

  /**
   * Returns the {@link Configuration} for this handler.
   *
   * @return {@link Configuration} for this handler
   */
  public abstract Configuration configuration();

  /**
   * Returns the class type of the model to be ingested by StreamX.
   *
   * @return class type of the model to be ingested by StreamX
   */
  public abstract Class<T> modelClass();

  /**
   * Model to be ingested by StreamX.
   *
   * @return model to be ingested by StreamX
   */
  public abstract T model(InputStream inputStream);
}
