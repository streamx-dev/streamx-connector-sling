package dev.streamx.sling.connector.testing.streamx.clients.ingestion;

import static org.mockito.Mockito.spy;

import dev.streamx.clients.ingestion.StreamxClient;
import dev.streamx.clients.ingestion.publisher.Publisher;
import dev.streamx.sling.connector.PublicationAction;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.list.UnmodifiableList;

public class FakeStreamxClient implements StreamxClient {

  private final List<Publication> publications = new ArrayList<>();
  private Publisher<?> lastPublisher;

  @Override
  public <T> Publisher<T> newPublisher(String channel, Class<T> modelClass) {
    FakePublisher<T> fakePublisher = spy(new FakePublisher<>(this, channel));
    lastPublisher = fakePublisher;
    return fakePublisher;
  }

  @Override
  public void close() {

  }

  public <T> void recordPublish(String key, String channel, T data) {
    publications.add(new Publication(PublicationAction.PUBLISH, key, channel, data));
  }

  public <T> void recordUnpublish(String key, String channel) {
    publications.add(new Publication(PublicationAction.UNPUBLISH, key, channel, null));
  }

  public List<Publication> getPublications() {
    return new UnmodifiableList<>(publications);
  }

  public Publisher<?> getLastPublisher() {
    return lastPublisher;
  }
}
