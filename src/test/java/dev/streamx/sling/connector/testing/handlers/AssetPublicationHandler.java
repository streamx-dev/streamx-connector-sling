package dev.streamx.sling.connector.testing.handlers;

import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.UnpublishData;
import org.apache.sling.api.resource.Resource;

public class AssetPublicationHandler implements PublicationHandler<Asset> {

  private static final String CHANNEL = "assets-inbox";

  @Override
  public String getId() {
    return "test-asset";
  }

  @Override
  public boolean canHandle(String resourcePath) {
    return resourcePath.startsWith("/content/dam");
  }

  @Override
  public PublishData<Asset> getPublishData(Resource resource) {
    return new PublishData<>(resource.getPath(), CHANNEL, Asset.class,
        new Asset(resource.getName()));
  }

  @Override
  public UnpublishData<Asset> getUnpublishData(String resourcePath) {
    return new UnpublishData<>(resourcePath, CHANNEL, Asset.class);
  }
}
