package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.StreamxClient;
import dev.streamx.sling.connector.testing.streamx.clients.ingestion.FakeStreamxClient;

public class FakeStreamxClientFactory implements StreamxClientFactory {

  private FakeStreamxClient fakeStreamxClient;

  @Override
  public StreamxClient createStreamxClient(String streamxUrl, String authToken) {
    fakeStreamxClient = new FakeStreamxClient();
    return fakeStreamxClient;
  }

  public FakeStreamxClient getFakeClient() {
    return fakeStreamxClient;
  }
}
