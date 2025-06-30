package dev.streamx.sling.connector.testing.handlers;

import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.UnpublishData;

public class ImpostorPublicationHandler implements PublicationHandler<Page> {

  @Override
  public String getId() {
    return "impostor-page";
  }

  @Override
  public boolean canHandle(ResourceInfo resource) {
    return resource.getPath().contains("impostor"); // Say you can handle...
  }

  @Override
  public PublishData<Page> getPublishData(ResourceInfo resourceInfo) {
    return null; // ...but return null...
  }

  @Override
  public UnpublishData<Page> getUnpublishData(ResourceInfo resourceInfo) {
    return null; // ...for both types of publications.
  }
}
