package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.StreamxClient;
import dev.streamx.clients.ingestion.publisher.Publisher;
import dev.streamx.sling.connector.PublicationData;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

public class StreamxInstanceClient {

  private final StreamxClient streamxClient;
  private final List<String> resourcePathPatterns;

  private final ConcurrentHashMap<String, Publisher<?>> publishers = new ConcurrentHashMap<>();

  StreamxInstanceClient(StreamxClient streamxClient, StreamxClientConfig config) {
    this.streamxClient = streamxClient;
    this.resourcePathPatterns = config.getResourcePathPatterns();
  }

  public <T> Publisher<T> getPublisher(PublicationData<T> publication) {
    return (Publisher<T>) publishers.computeIfAbsent(
        publication.getChannel(),
        channel -> streamxClient.newPublisher(channel, publication.getModelClass()));
  }

  boolean canProcess(String resourcePath) {
    if (resourcePathPatterns == null) {
      return false;
    }

    return resourcePathPatterns.stream().anyMatch(resourcePath::matches);
  }

}
