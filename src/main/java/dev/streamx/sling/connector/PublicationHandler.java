package dev.streamx.sling.connector;

public interface PublicationHandler<T> {

  String getId();

  boolean canHandle(String resourcePath);

  PublishData<T> getPublishData(String resourcePath) throws StreamxPublicationException;

  UnpublishData<T> getUnpublishData(String resourcePath) throws StreamxPublicationException;

}
