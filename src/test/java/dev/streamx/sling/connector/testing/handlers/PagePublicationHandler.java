package dev.streamx.sling.connector.testing.handlers;

import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.UnpublishData;
import java.util.Objects;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

public class PagePublicationHandler implements PublicationHandler<Page> {

  private static final String CHANNEL = "pages";
  private final ResourceResolver resourceResolver;

  public PagePublicationHandler(ResourceResolver resourceResolver) {
    this.resourceResolver = resourceResolver;
  }

  private static String getPagePath(String resourcePath) {
    return resourcePath + ".html";
  }

  @Override
  public String getId() {
    return "test-page";
  }

  @Override
  public boolean canHandle(String resourcePath) {
    return resourcePath.startsWith("/content/my-site");
  }

  @Override
  public PublishData<Page> getPublishData(String resourcePath) {
    Resource resource = resourceResolver.getResource(resourcePath);
    Objects.requireNonNull(resource);
    return new PublishData<>(getPagePath(resourcePath), CHANNEL, Page.class,
        new Page(resource.getName()));
  }

  @Override
  public UnpublishData<Page> getUnpublishData(String resourcePath) {
    return new UnpublishData<>(getPagePath(resourcePath), CHANNEL, Page.class);
  }
}
