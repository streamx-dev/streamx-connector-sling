package dev.streamx.sling.connector.paths;

import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.servlethelpers.internalrequests.SlingInternalRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class InternalRequestForResource {

  private static final Logger LOG = LoggerFactory.getLogger(InternalRequestForResource.class);

  private final ResourceResolver resourceResolver;
  private final SlingRequestProcessor slingRequestProcessor;
  private final String resourcePath;
  private final String extension;

  InternalRequestForResource(
      Resource resource, SlingRequestProcessor slingRequestProcessor, String extension
  ) {
    this.resourceResolver = resource.getResourceResolver();
    this.slingRequestProcessor = slingRequestProcessor;
    this.resourcePath = resource.getPath();
    this.extension = extension;
  }

  String contentType() {
    try {
      String contentType = new SlingInternalRequest(
          resourceResolver, slingRequestProcessor, resourcePath
      ).withExtension(extension)
          .withParameter("wcmmode", "disabled")
          .execute()
          .getResponse()
          .getContentType();
      LOG.trace(
          "Content type for a resource at path '{}' with extension '{}': '{}'",
          resourcePath, extension, contentType
      );
      return contentType;
    } catch (IOException exception) {
      String message = String.format(
          "Failed to determine content type for a resource at path '%s' with extension '%s'",
          resourcePath, extension
      );
      LOG.error(message, exception);
      return StringUtils.EMPTY;
    }
  }

  String getResponseAsString() {
    try {
      String responseAsString = new SlingInternalRequest(
          resourceResolver, slingRequestProcessor, resourcePath
      ).withExtension(extension)
       .withParameter("wcmmode", "disabled")
       .execute()
       .getResponseAsString();
      LOG.debug(
          "Generated response as string for '{}'. Response length: {}",
          resourcePath, responseAsString.length()
      );
      return responseAsString;
    } catch (IOException exception) {
      String message = String.format(
          "Failed to generate response as string for '%s'", resourcePath
      );
      LOG.error(message, exception);
      return StringUtils.EMPTY;
    }
  }
}
