package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.exceptions.StreamxClientException;

public interface StreamxClientFactory {

  StreamxInstanceClient createStreamxClient(StreamxClientConfig config) throws StreamxClientException;

}
