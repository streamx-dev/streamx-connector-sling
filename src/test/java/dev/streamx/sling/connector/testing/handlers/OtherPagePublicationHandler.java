package dev.streamx.sling.connector.testing.handlers;

import org.apache.sling.api.resource.ResourceResolver;

public class OtherPagePublicationHandler extends AbstractPagePublicationHandler {

  public OtherPagePublicationHandler(ResourceResolver resourceResolver) {
    super(resourceResolver);
  }

  @Override
  public String getId() {
    return "other-test-page";
  }

  @Override
  protected String handledPagePathPrefix() {
    return "/content/other-site";
  }
}
