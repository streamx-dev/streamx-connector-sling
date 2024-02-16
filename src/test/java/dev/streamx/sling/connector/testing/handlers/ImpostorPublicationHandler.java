package dev.streamx.sling.connector.testing.handlers;

import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.UnpublishData;
import org.apache.sling.api.resource.Resource;

public class ImpostorPublicationHandler implements PublicationHandler<Page> {

  @Override
  public String getId() {
    return "impostor-page";
  }

  @Override
  public boolean canHandle(String resourcePath) {
    return resourcePath.contains("impostor"); // Say you can handle...
  }

  @Override
  public PublishData<Page> getPublishData(Resource resource) {
    return null; // ...but return null...
  }

  @Override
  public UnpublishData<Page> getUnpublishData(String resourcePath) {
    return null; // ...for both types of publications.
  }
}
