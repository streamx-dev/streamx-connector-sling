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

  /**
   * Returns the body of the HTTP response represented as {@link String}.
   *
   * @return body of the HTTP response represented as {@link String}; {@link StringUtils#EMPTY} is
   * returned if the response body cannot be retrieved
   */
  public String getResponseAsString() {
    InternalRequest internalRequest = createInternalRequest();
    try {
      return internalRequest.execute().getResponseAsString();
    } catch (IOException exception) {
      LOG.error("Failed to get response as string for '{}'", slingUri, exception);
    }

    return StringUtils.EMPTY;
  }

  /**
   * Returns the {@link Optional} containing the body of the HTTP response.
   *
   * @return {@link Optional} containing the body of the HTTP response; empty {@link Optional} is
   * returned if the response body cannot be retrieved
   */
  public Optional<InputStream> getResponseAsInputStream() {
    return getResponseAsBytes().map(ByteArrayInputStream::new);
  }

  /**
   * Returns {@code byte[]} containing the content of the resource under the {@code slingUri} given via constructor.
   *
   * @return {@code byte[]} containing the resource bytes; empty {@link Optional} is
   * returned if the response body cannot be retrieved
   */
  public Optional<byte[]> getResponseAsBytes() {
    InternalRequest internalRequest = createInternalRequest();
    try {
      SlingHttpServletResponse response = internalRequest.execute().getResponse();
      if (response instanceof MockSlingHttpServletResponse) {
        byte[] output = ((MockSlingHttpServletResponse) response).getOutput();
        return Optional.of(output);
      }
    } catch (IOException exception) {
      LOG.error("Failed to get response as bytes for '{}'", slingUri, exception);
    }

    return Optional.empty();
  }

  private InternalRequest createInternalRequest() {
    Map<String, Object> pathParameters = createPathParametersMap();
    LOG.trace("Creating internal request for '{}' with path parameters {}", slingUri, pathParameters);
    return new SlingInternalRequest(resourceResolver, slingRequestProcessor, slingUri.toString())
        .withParameters(pathParameters);
  }

  private Map<String, Object> createPathParametersMap() {
    Map<String, Object> pathParameters = new LinkedHashMap<>(slingUri.getPathParameters());
    pathParameters.put("wcmmode", "disabled");
    pathParameters.putAll(additionalProperties);
    return pathParameters;
  }
}
