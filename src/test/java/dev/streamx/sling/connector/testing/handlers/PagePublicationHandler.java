package dev.streamx.sling.connector.testing.handlers;

import org.apache.sling.api.resource.ResourceResolver;

public class PagePublicationHandler extends AbstractPagePublicationHandler {

  public PagePublicationHandler(ResourceResolver resourceResolver) {
    super(resourceResolver);
  }

  @Override
  public String getId() {
    return "test-page";
  }

  @Override
  protected String handledPagePathPrefix() {
    return "/content/my-site";
  }
}
