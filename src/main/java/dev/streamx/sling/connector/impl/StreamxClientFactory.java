package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.exceptions.StreamxClientException;

/**
 * Creates new instances of {@link StreamxInstanceClient}.
 */
@FunctionalInterface
public interface StreamxClientFactory {

  /**
   * Creates a new {@link StreamxInstanceClient} instance.
   *
   * @param config the configuration for the new client
   * @return a new {@link StreamxInstanceClient} instance
   * @throws StreamxClientException if an error occurs while creating the client
   */
  StreamxInstanceClient createStreamxClient(StreamxClientConfig config)
      throws StreamxClientException;

}
