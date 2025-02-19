package dev.streamx.sling.connector.testing.handlers;

import dev.streamx.sling.connector.IngestionDataFactory;
import dev.streamx.sling.connector.IngestionDataKey;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.UnpublishData;
import java.util.Objects;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

public class PageIngestionDataFactory implements IngestionDataFactory<Page> {

  private static final String CHANNEL = "pages";
  private final ResourceResolver resourceResolver;

  public PageIngestionDataFactory(ResourceResolver resourceResolver) {
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
  public boolean canProduce(IngestionDataKey ingestionDataKey) {
    return ingestionDataKey.get().startsWith("/content/my-site");
  }

  @Override
  public PublishData<Page> producePublishData(IngestionDataKey ingestionDataKey) {
    Resource resource = resourceResolver.getResource(ingestionDataKey.get());
    Objects.requireNonNull(resource);
    return new PublishData<>(() -> getPagePath(ingestionDataKey.get()), () -> CHANNEL, Page.class,
        new Page(resource.getName()));
  }

  @Override
  public UnpublishData<Page> produceUnpublishData(IngestionDataKey ingestionDataKey) {
    return new UnpublishData<>(() -> getPagePath(ingestionDataKey.get()), () -> CHANNEL,
        Page.class);
  }
}
