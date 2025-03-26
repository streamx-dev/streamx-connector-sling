package dev.streamx.sling.connector.testing.handlers;

import dev.streamx.sling.connector.IngestedData;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.UnpublishData;

public class ImpostorPublicationHandler implements PublicationHandler<Page> {

  @Override
  public String getId() {
    return "impostor-page";
  }

  @Override
  public boolean canHandle(IngestedData ingestedData) {
    return ingestedData.uriToIngest().toString().contains("impostor"); // Say you can handle...
  }

  @Override
  public PublishData<Page> getPublishData(String resourcePath) {
    return null; // ...but return null...
  }

  @Override
  public UnpublishData<Page> getUnpublishData(String resourcePath) {
    return null; // ...for both types of publications.
  }
}
