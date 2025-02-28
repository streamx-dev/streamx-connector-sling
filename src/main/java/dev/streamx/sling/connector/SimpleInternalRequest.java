package dev.streamx.sling.connector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.servlethelpers.MockSlingHttpServletResponse;
import org.apache.sling.servlethelpers.internalrequests.InternalRequest;
import org.apache.sling.servlethelpers.internalrequests.SlingInternalRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple internal HTTP request to a {@link SlingUri}.
 */
@SuppressWarnings("WeakerAccess")
public class SimpleInternalRequest {

  private static final Logger LOG = LoggerFactory.getLogger(SimpleInternalRequest.class);

  private final ResourceResolver resourceResolver;
  private final SlingRequestProcessor slingRequestProcessor;
  private final SlingUri slingUri;

  /**
   * Constructs a new instance of this class.
   *
   * @param slingUri              {@link SlingUri} to request
   * @param slingRequestProcessor {@link SlingRequestProcessor} to use for request processing
   * @param resourceResolver      {@link ResourceResolver} to use for accessing {@link Resource}s
   */
  public SimpleInternalRequest(
      SlingUri slingUri,
      SlingRequestProcessor slingRequestProcessor,
      ResourceResolver resourceResolver
  ) {
    this.resourceResolver = resourceResolver;
    this.slingUri = slingUri;
    this.slingRequestProcessor = slingRequestProcessor;
  }

  /**
   * Returns the content type of the HTTP response.
   *
   * @return content type of the HTTP response
   */
  public String contentType() {
    try {
      String contentType = internalRequest()
          .execute()
          .getResponse()
          .getContentType();
      LOG.trace("Content type for '{}' is '{}'", slingUri, contentType);
      return contentType;
    } catch (IOException exception) {
      String message = String.format("Failed to determine content type for '%s'", slingUri);
      LOG.error(message, exception);
      return StringUtils.EMPTY;
    }
  }

  /**
   * Returns the {@link Optional} containing the body of the HTTP response.
   *
   * @return {@link Optional} containing the body of the HTTP response; empty {@link Optional} is
   * returned if the response body cannot be retrieved
   */
  public Optional<InputStream> getResponseAsInputStream() {
    try {
      SlingHttpServletResponse response = internalRequest()
          .execute()
          .getResponse();
      Optional<InputStream> inputStream = Optional.of(response)
          .filter(MockSlingHttpServletResponse.class::isInstance)
          .map(MockSlingHttpServletResponse.class::cast)
          .map(MockSlingHttpServletResponse::getOutput)
          .map(ByteArrayInputStream::new);
      LOG.debug("Generated {} for '{}'", inputStream, slingUri);
      return inputStream;
    } catch (IOException exception) {
      String message = String.format("Failed to generate response for '%s'", slingUri);
      LOG.error(message, exception);
      return Optional.empty();
    }
  }

  private InternalRequest internalRequest() {
    AbstractMap.SimpleEntry<String, String> wcmmode = new AbstractMap.SimpleEntry<>(
        "wcmmode", "disabled"
    );
    Map<String, Object> pathParameters = Stream.concat(
        slingUri.getPathParameters().entrySet().stream(), Stream.of(wcmmode)
    ).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    String path = Optional.ofNullable(slingUri.getResourcePath()).orElse(StringUtils.EMPTY);
    LOG.trace("Creating internal request for '{}'. Resolved path: {}", slingUri, path);
    return new SlingInternalRequest(
        resourceResolver, slingRequestProcessor, path
    ).withExtension(slingUri.getExtension())
        .withSelectors(slingUri.getSelectors())
        .withParameters(pathParameters);
  }

  /**
   * Returns the body of the HTTP response represented as {@link String}.
   *
   * @return body of the HTTP response represented as {@link String}; {@link StringUtils#EMPTY} is
   * returned if the response body cannot be retrieved
   */
  public String getResponseAsString() {
    try {
      String responseAsString = internalRequest()
          .execute()
          .getResponseAsString();
      LOG.debug(
          "Generated response as string for '{}'. Response length: {}",
          slingUri, responseAsString.length()
      );
      return responseAsString;
    } catch (IOException exception) {
      String message = String.format("Failed to generate response as string for '%s'", slingUri);
      LOG.error(message, exception);
      return StringUtils.EMPTY;
    }
  }
}
