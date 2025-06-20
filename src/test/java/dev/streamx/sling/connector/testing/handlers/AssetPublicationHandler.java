package dev.streamx.sling.connector.testing.handlers;

import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.UnpublishData;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

public class AssetPublicationHandler implements PublicationHandler<Asset> {

  private static final String CHANNEL = "assets";

  private final ResourceResolver resourceResolver;

  public AssetPublicationHandler(ResourceResolver resourceResolver) {
    this.resourceResolver = resourceResolver;
  }

  @Override
  public String getId() {
    return "test-asset";
  }

  @Override
  public boolean canHandle(ResourceInfo resource) {
    return "dam:Asset".equals(resource.getPrimaryNodeType())
           && !resource.getPath().contains("/related/");
  }

  @Override
  public PublishData<Asset> getPublishData(String resourcePath) {
    Resource resource = resourceResolver.getResource(resourcePath);
    Objects.requireNonNull(resource);
    return new PublishData<>(resourcePath, CHANNEL, Asset.class,
        new Asset(resource.getName().getBytes(StandardCharsets.UTF_8)));
  }

  @Override
  public UnpublishData<Asset> getUnpublishData(String resourcePath) {
    return new UnpublishData<>(resourcePath, CHANNEL, Asset.class);
  }
}
