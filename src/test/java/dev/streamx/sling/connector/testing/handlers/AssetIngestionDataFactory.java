package dev.streamx.sling.connector.testing.handlers;

import dev.streamx.sling.connector.IngestionDataFactory;
import dev.streamx.sling.connector.IngestionDataKey;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.UnpublishData;
import java.util.Objects;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

public class AssetIngestionDataFactory implements IngestionDataFactory<Asset> {

  private static final String CHANNEL = "assets";

  private final ResourceResolver resourceResolver;

  public AssetIngestionDataFactory(ResourceResolver resourceResolver) {
    this.resourceResolver = resourceResolver;
  }

  @Override
  public String getId() {
    return "test-asset";
  }

  @Override
  public boolean canProduce(IngestionDataKey ingestionDataKey) {
    return ingestionDataKey.get().startsWith("/content/dam");
  }

  @Override
  public PublishData<Asset> producePublishData(IngestionDataKey ingestionDataKey) {
    Resource resource = resourceResolver.getResource(ingestionDataKey.get());
    Objects.requireNonNull(resource);
    return new PublishData<>(ingestionDataKey, () -> CHANNEL, Asset.class,
        new Asset(resource.getName()));
  }

  @Override
  public UnpublishData<Asset> produceUnpublishData(IngestionDataKey ingestionDataKey) {
    return new UnpublishData<>(ingestionDataKey, () -> CHANNEL, Asset.class);
  }
}
