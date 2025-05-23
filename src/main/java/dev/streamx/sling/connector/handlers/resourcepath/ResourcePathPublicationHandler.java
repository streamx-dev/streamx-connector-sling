package dev.streamx.sling.connector.handlers.resourcepath;

import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.UnpublishData;
import dev.streamx.sling.connector.util.SimpleInternalRequest;
import java.io.IOException;
import java.io.InputStream;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.api.uri.SlingUriBuilder;
import org.apache.sling.engine.SlingRequestProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link PublicationHandler} for resources that are retrieved via internal requests to extract the
 * resource path which is published.
 *
 * @param <T> type of the model to be ingested by StreamX
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class ResourcePathPublicationHandler<T> implements PublicationHandler<T> {

  private static final Logger LOG = LoggerFactory.getLogger(ResourcePathPublicationHandler.class);
  private final ResourceResolverFactory resourceResolverFactory;
  private final SlingRequestProcessor slingRequestProcessor;

  /**
   * Constructs an instance of this class.
   *
   * @param resourceResolverFactory {@link ResourceResolverFactory} to use when accessing resources
   * @param slingRequestProcessor   {@link SlingRequestProcessor} to use when retrieving resource
   *                                content
   */
  protected ResourcePathPublicationHandler(
      ResourceResolverFactory resourceResolverFactory,
      SlingRequestProcessor slingRequestProcessor
  ) {
    this.resourceResolverFactory = resourceResolverFactory;
    this.slingRequestProcessor = slingRequestProcessor;
  }

  @Override
  public boolean canHandle(ResourceInfo resource) {
    String resourcePath = resource.getPath();
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
      return generatePublishData(resourcePath, resourceResolver);
    } catch (LoginException | IOException exception) {
      throw new StreamxPublicationException(
          String.format("Failed to generate publish data for '%s'", resourcePath), exception
      );
    }
  }

  private PublishData<T> generatePublishData(
      String resourcePath, ResourceResolver resourceResolver
  ) throws IOException {
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
      return publishData;
    }
  }

  @Override
  public UnpublishData<T> getUnpublishData(String resourcePath) {
    String channel = configuration().channel();
    Class<T> modelClass = modelClass();
    return new UnpublishData<>(resourcePath, channel, modelClass);
  }

  /**
   * Returns the {@link ResourcePathPublicationHandlerConfig} for this handler.
   *
   * @return {@link ResourcePathPublicationHandlerConfig} for this handler
   */
  public abstract ResourcePathPublicationHandlerConfig configuration();

  /**
   * Returns the class type of the model to be ingested by StreamX.
   *
   * @return class type of the model to be ingested by StreamX
   */
  public abstract Class<T> modelClass();

  /**
   * Model to be ingested by StreamX.
   *
   * @param inputStream {@link InputStream} to be wrapped in the model
   * @return model to be ingested by StreamX
   */
  public abstract T model(InputStream inputStream);
}
