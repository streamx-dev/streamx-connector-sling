package dev.streamx.sling.connector.testing.streamx.clients.ingestion;

import dev.streamx.clients.ingestion.publisher.Message;
import dev.streamx.clients.ingestion.publisher.Publisher;
import dev.streamx.clients.ingestion.publisher.SuccessResult;
import dev.streamx.sling.connector.PublicationAction;

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
    if (message.getAction().equals(Message.PUBLISH_ACTION)) {
      fakeStreamxClient.recordPublish(message.getKey(), channel, message.getPayload());
    } else if (message.getAction().equals(Message.UNPUBLISH_ACTION)) {
      fakeStreamxClient.recordUnpublish(message.getKey(), channel);
    }
    return null;
  }

  @Override
  public SuccessResult send(T t) {
    throw new UnsupportedOperationException();
  }
}
