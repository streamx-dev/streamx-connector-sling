package dev.streamx.sling.connector.testing.streamx.clients.ingestion;

import dev.streamx.clients.ingestion.StreamxClient;
import dev.streamx.clients.ingestion.publisher.Publisher;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.list.UnmodifiableList;

public class FakeStreamxClient implements StreamxClient {

  private final List<Publication> publications = new ArrayList<>();

  @Override
  public <T> Publisher<T> newPublisher(String channel, Class<T> modelClass) {
    return new FakePublisher<>(this, channel);
  }

  @Override
  public void close() {

  }

  public <T> void recordPublish(String key, String channel, T data) {
    publications.add(new Publication("Publish", key, channel, data));
  }

  public <T> void recordUnpublish(String key) {
    publications.add(new Publication("Unpublish", key, null, null));
  }

  public List<Publication> getPublications() {
    return new UnmodifiableList<>(publications);
  }

  public static class Publication {

    private final String action;
    private final String key;
    private final String channel;
    private final String data;

    public Publication(String action, String key, String channel, Object data) {
      this.action = action;
      this.key = key;
      this.channel = channel;
      this.data = data != null ? data.toString() : null;
    }

    public String getAction() {
      return action;
    }

    public String getKey() {
      return key;
    }

    public String getChannel() {
      return channel;
    }

    public String getData() {
      return data;
    }
  }
}
