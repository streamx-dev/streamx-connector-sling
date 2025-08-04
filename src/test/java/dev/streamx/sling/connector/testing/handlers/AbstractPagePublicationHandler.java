package dev.streamx.sling.connector.testing.handlers;

import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.UnpublishData;
import java.util.Objects;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

abstract class AbstractPagePublicationHandler implements PublicationHandler<Page> {

  private static final String CHANNEL = "pages";
  private final ResourceResolver resourceResolver;

  public AbstractPagePublicationHandler(ResourceResolver resourceResolver) {
    this.resourceResolver = resourceResolver;
  }

  protected abstract String handledPagePathPrefix();

  @Override
  public boolean canHandle(ResourceInfo resource) {
    return "cq:Page".equals(resource.getProperty(JcrConstants.JCR_PRIMARYTYPE))
           && resource.getPath().startsWith(handledPagePathPrefix());
  }

  @Override
  public PublishData<Page> getPublishData(ResourceInfo resourceInfo) {
    Resource resource = resourceResolver.getResource(resourceInfo.getPath());
    Objects.requireNonNull(resource);
    return new PublishData<>(getPagePath(resourceInfo), CHANNEL, Page.class,
        new Page(resource.getName()));
  }

  @Override
  public UnpublishData<Page> getUnpublishData(ResourceInfo resourceInfo) {
    return new UnpublishData<>(getPagePath(resourceInfo), CHANNEL, Page.class);
  }

  private static String getPagePath(ResourceInfo resource) {
    return resource.getPath() + ".html";
  }
}
