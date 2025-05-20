package dev.streamx.sling.connector.testing.handlers;

import dev.streamx.sling.connector.ResourceToIngest;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.UnpublishData;
import java.util.Objects;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

abstract class AbstractPagePublicationHandler implements PublicationHandler<Page> {

  private static final String CHANNEL = "pages";
  private final ResourceResolver resourceResolver;

  public AbstractPagePublicationHandler(ResourceResolver resourceResolver) {
    this.resourceResolver = resourceResolver;
  }

  private static String getPagePath(String resourcePath) {
    return resourcePath + ".html";
  }

  protected abstract String handledPagePathPrefix();

  @Override
  public boolean canHandle(ResourceToIngest resource) {
    return resource.getPrimaryNodeType().equals("cq:Page")
           && resource.getPath().startsWith(handledPagePathPrefix());
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
