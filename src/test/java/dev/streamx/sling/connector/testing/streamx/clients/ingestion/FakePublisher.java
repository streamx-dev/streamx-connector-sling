package dev.streamx.sling.connector.testing.streamx.clients.ingestion;

import dev.streamx.clients.ingestion.publisher.Message;
import dev.streamx.clients.ingestion.publisher.Publisher;
import dev.streamx.clients.ingestion.publisher.SuccessResult;

public class FakePublisher<T> implements Publisher<T> {

  private final FakeStreamxClient fakeStreamxClient;
  private final String channel;

  public FakePublisher(FakeStreamxClient fakeStreamxClient, String channel) {
    this.fakeStreamxClient = fakeStreamxClient;
    this.channel = channel;
  }

  @Override
  public SuccessResult publish(String key, T data) {
    fakeStreamxClient.recordPublish(key, channel, data);
    return null;
  }

  @Override
  public SuccessResult unpublish(String key) {
    fakeStreamxClient.recordUnpublish(key, channel);
    return null;
  }

  @Override
  public SuccessResult send(Message<T> message) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SuccessResult send(T t) {
    throw new UnsupportedOperationException();
  }
}
