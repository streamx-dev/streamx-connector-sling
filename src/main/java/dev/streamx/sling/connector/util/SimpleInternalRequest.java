package dev.streamx.sling.connector.util;

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
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
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
   * @param slingUri                {@link SlingUri} to request
   * @param slingRequestProcessor   {@link SlingRequestProcessor} to use for request processing
   * @param resourceResolver        {@link ResourceResolver} to use for resource resolution
   */
  public SimpleInternalRequest(
      SlingUri slingUri,
      SlingRequestProcessor slingRequestProcessor,
      ResourceResolver resourceResolver
  ) {
    this.slingUri = slingUri;
    this.slingRequestProcessor = slingRequestProcessor;
    this.resourceResolver = resourceResolver;
  }

  // TODO remove when package version changed to 2.0.0
  /**
   * Constructs a new instance of this class.
   *
   * @param slingUri                {@link SlingUri} to request
   * @param slingRequestProcessor   {@link SlingRequestProcessor} to use for request processing
   * @param resourceResolverFactory {@link ResourceResolverFactory} to use for resource resolution
   */
  public SimpleInternalRequest(
      SlingUri slingUri,
      SlingRequestProcessor slingRequestProcessor,
      ResourceResolverFactory resourceResolverFactory
  ) {
    throw new UnsupportedOperationException("Use the constructor with ResourceResolver instead");
  }

  private Optional<SlingHttpServletResponse> extractResponse(InternalRequest internalRequest) {
    try {
      SlingHttpServletResponse response = internalRequest.getResponse();
      return Optional.of(response);
    } catch (IOException exception) {
      String message = String.format(
          "Failed to extract response for '%s' from '%s'", slingUri, internalRequest
      );
      LOG.error(message, exception);
      return Optional.empty();
    }
  }

  /**
   * Returns the {@link Optional} containing the body of the HTTP response.
   *
   * @return {@link Optional} containing the body of the HTTP response; empty {@link Optional} is
   * returned if the response body cannot be retrieved
   */
  public Optional<InputStream> getResponseAsInputStream() {
    return executedInternalRequest(slingUri)
        .flatMap(this::extractResponse)
        .filter(MockSlingHttpServletResponse.class::isInstance)
        .map(MockSlingHttpServletResponse.class::cast)
        .map(MockSlingHttpServletResponse::getOutput)
        .map(
            output -> {
              LOG.debug("Generated output of {} bytes for '{}'", output.length, slingUri);
              return new ByteArrayInputStream(output);
            }
        );
  }

  private Optional<InternalRequest> executedInternalRequest(SlingUri slingUri) {
    AbstractMap.SimpleEntry<String, String> wcmmode = new AbstractMap.SimpleEntry<>(
        "wcmmode", "disabled"
    );
    Map<String, Object> pathParameters = Stream.concat(
        slingUri.getPathParameters().entrySet().stream(), Stream.of(wcmmode)
    ).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    LOG.trace("Creating internal request for '{}'", slingUri);
    try {
      InternalRequest executedInternalRequest = new SlingInternalRequest(
          resourceResolver, slingRequestProcessor, slingUri.toString()
      ).withParameters(pathParameters).execute();
      return Optional.of(executedInternalRequest);
    } catch (IOException exception) {
      String message = String.format("Failed to execute internal request for '%s'", slingUri);
      LOG.error(message, exception);
      return Optional.empty();
    }
  }

  /**
   * Returns the body of the HTTP response represented as {@link String}.
   *
   * @return body of the HTTP response represented as {@link String}; {@link StringUtils#EMPTY} is
   * returned if the response body cannot be retrieved
   */
  public String getResponseAsString() {
    String responseAsString = executedInternalRequest(slingUri)
        .flatMap(
            internalRequest -> {
              try {
                return Optional.of(internalRequest.getResponseAsString());
              } catch (IOException exception) {
                String message = String.format(
                    "Failed to get response as string for '%s'", slingUri
                );
                LOG.error(message, exception);
                return Optional.empty();
              }

            }
        ).orElse(StringUtils.EMPTY);
    LOG.debug(
        "Generated response as string for '{}'. Response length: {}",
        slingUri, responseAsString.length()
    );
    return responseAsString;
  }
}
