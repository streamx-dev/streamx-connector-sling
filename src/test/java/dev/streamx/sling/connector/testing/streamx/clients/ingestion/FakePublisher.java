package dev.streamx.sling.connector.testing.streamx.clients.ingestion;

import dev.streamx.clients.ingestion.publisher.Publisher;

public class FakePublisher<T> implements Publisher<T> {

  private final FakeStreamxClient fakeStreamxClient;
  private final String channel;

  public FakePublisher(FakeStreamxClient fakeStreamxClient, String channel) {
    this.fakeStreamxClient = fakeStreamxClient;
    this.channel = channel;
  }

  @Override
  public Long publish(String key, T data) {
    fakeStreamxClient.recordPublish(key, channel, data);
    return null;
  }

  @Override
  public Long unpublish(String key) {
    fakeStreamxClient.recordUnpublish(key);
    return null;
  }
}
