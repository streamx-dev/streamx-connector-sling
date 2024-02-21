package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.StreamxClient;
import dev.streamx.clients.ingestion.exceptions.StreamxClientException;

public interface StreamxClientFactory {

  StreamxClient createStreamxClient(String streamxUrl, String authToken)
      throws StreamxClientException;

}
