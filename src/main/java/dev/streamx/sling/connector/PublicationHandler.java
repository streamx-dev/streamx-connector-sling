package dev.streamx.sling.connector;

import org.apache.sling.api.resource.Resource;

public interface PublicationHandler<T> {

  String getId();

  boolean canHandle(String resourcePath);

  PublishData<T> getPublishData(Resource resource);

  UnpublishData<T> getUnpublishData(String resourcePath);

}
