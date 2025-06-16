package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.StreamxClient;
import dev.streamx.clients.ingestion.exceptions.StreamxClientException;
import dev.streamx.clients.ingestion.publisher.Publisher;
import dev.streamx.sling.connector.PublicationData;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client of single StreamX instance.
 */
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

  <T> Publisher<T> getPublisher(PublicationData<T> publication) throws StreamxClientException {
    String channel = publication.getChannel();

    if (!publishersByChannel.containsKey(channel)) {
      publishersByChannel.put(
          channel,
          streamxClient.newPublisher(channel, publication.getModelClass())
      );
    }

    return (Publisher<T>) publishersByChannel.get(channel);
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
