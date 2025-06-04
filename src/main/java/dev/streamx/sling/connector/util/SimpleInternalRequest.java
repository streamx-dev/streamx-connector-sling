package dev.streamx.sling.connector.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletResponse;
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
  private final Map<String, String> additionalProperties;

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
    this(slingUri, slingRequestProcessor, resourceResolver, Collections.emptyMap());
  }

  /**
   * Constructs a new instance of this class.
   *
   * @param slingUri                {@link SlingUri} to request
   * @param slingRequestProcessor   {@link SlingRequestProcessor} to use for request processing
   * @param resourceResolver        {@link ResourceResolver} to use for resource resolution
   * @param additionalProperties    Map of additional properties that will be added as parameters of the issued {@link SlingInternalRequest}
   */
  public SimpleInternalRequest(
      SlingUri slingUri,
      SlingRequestProcessor slingRequestProcessor,
      ResourceResolver resourceResolver,
      Map<String, String> additionalProperties
  ) {
    this.slingUri = slingUri;
    this.slingRequestProcessor = slingRequestProcessor;
    this.resourceResolver = resourceResolver;
    this.additionalProperties = Collections.unmodifiableMap(additionalProperties);
  }

  private Optional<SlingHttpServletResponse> extractResponse(InternalRequest internalRequest) {
    try {
      SlingHttpServletResponse response = internalRequest.getResponse();
      return Optional.of(response);
    } catch (IOException exception) {
      LOG.error("Failed to extract response for '{}' from '{}'", slingUri, internalRequest, exception);
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
    Map<String, Object> pathParameters = createPathParametersMap(slingUri);
    LOG.trace("Creating internal request for '{}' with path parameters {}", slingUri, pathParameters);
    try {
      InternalRequest executedInternalRequest = new SlingInternalRequest(
          resourceResolver, slingRequestProcessor, slingUri.toString()
      ).withParameters(pathParameters).execute();
      return Optional.of(executedInternalRequest);
    } catch (IOException exception) {
      LOG.error("Failed to execute internal request for '{}'", slingUri, exception);
      return Optional.empty();
    }
  }

  private Map<String, Object> createPathParametersMap(SlingUri slingUri) {
    Map<String, Object> pathParameters = new LinkedHashMap<>(slingUri.getPathParameters());
    pathParameters.put("wcmmode", "disabled");
    pathParameters.putAll(additionalProperties);
    return pathParameters;
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
                LOG.error("Failed to get response as string for '{}'", slingUri, exception);
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
