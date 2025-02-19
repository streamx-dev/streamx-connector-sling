package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.StreamxClient;
import dev.streamx.clients.ingestion.exceptions.StreamxClientException;
import dev.streamx.clients.ingestion.publisher.Publisher;
import dev.streamx.sling.connector.IngestionData;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class StreamxInstanceClient {

  private final StreamxClient streamxClient;
  private final List<String> resourcePathPatterns;
  private final String name;

  private final ConcurrentHashMap<String, Publisher<?>> publishersByChannel =
      new ConcurrentHashMap<>();

  StreamxInstanceClient(StreamxClient streamxClient, StreamxClientConfig config) {
    this.streamxClient = streamxClient;
    this.resourcePathPatterns = config.getResourcePathPatterns();
    this.name = config.getName();
  }

  <T> Publisher<T> getPublisher(IngestionData<T> publication) throws StreamxClientException {
    try {
      return (Publisher<T>) publishersByChannel.computeIfAbsent(
          publication.channel().name(),
          channel -> {
            try {
              return streamxClient.newPublisher(channel, publication.modelClass());
            } catch (StreamxClientException e) {
              throw new RuntimeException(e);
            }
          });
    } catch (RuntimeException e) {
      throw new StreamxClientException("Cannot create publisher", e);
    }
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
