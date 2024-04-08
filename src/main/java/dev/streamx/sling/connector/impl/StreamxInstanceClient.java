package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.StreamxClient;
import dev.streamx.clients.ingestion.publisher.Publisher;
import dev.streamx.sling.connector.PublicationData;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class StreamxInstanceClient {

  private final StreamxClient streamxClient;
  private final List<String> resourcePathPatterns;
  private final String name;

  private final ConcurrentHashMap<String, Publisher<?>> publishersByChannel = new ConcurrentHashMap<>();

  StreamxInstanceClient(StreamxClient streamxClient, StreamxClientConfig config) {
    this.streamxClient = streamxClient;
    this.resourcePathPatterns = config.getResourcePathPatterns();
    this.name = config.getName();
  }

  <T> Publisher<T> getPublisher(PublicationData<T> publication) {
    return (Publisher<T>) publishersByChannel.computeIfAbsent(
        publication.getChannel(),
        channel -> streamxClient.newPublisher(channel, publication.getModelClass()));
  }

  String getName() {
    return name;
  }

  boolean canProcess(String resourcePath) {
    if (resourcePathPatterns == null) {
      return false;
    }

    return resourcePathPatterns.stream().anyMatch(resourcePath::matches);
  }

}
