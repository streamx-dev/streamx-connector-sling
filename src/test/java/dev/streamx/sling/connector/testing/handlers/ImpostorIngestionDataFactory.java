package dev.streamx.sling.connector.testing.handlers;

import dev.streamx.sling.connector.IngestionDataFactory;
import dev.streamx.sling.connector.IngestionDataKey;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.UnpublishData;

public class ImpostorIngestionDataFactory implements IngestionDataFactory<Page> {

  @Override
  public String getId() {
    return "impostor-page";
  }

  @Override
  public boolean canProduce(IngestionDataKey ingestionDataKey) {
    return ingestionDataKey.get().contains("impostor"); // Say you can handle...
  }

  @Override
  public PublishData<Page> producePublishData(IngestionDataKey ingestionDataKey) {
    return null; // ...but return null...
  }

  @Override
  public UnpublishData<Page> produceUnpublishData(IngestionDataKey ingestionDataKey) {
    return null; // ...for both types of publications.
  }
}
