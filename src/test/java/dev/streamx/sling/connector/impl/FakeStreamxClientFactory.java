package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.testing.streamx.clients.ingestion.FakeStreamxClient;
import java.util.HashMap;
import java.util.Map;

public class FakeStreamxClientFactory implements StreamxClientFactory {

  private final Map<String, FakeStreamxClient> fakeClients = new HashMap<>();

  @Override
  public StreamxInstanceClient createStreamxClient(StreamxClientConfig config) {
    FakeStreamxClient fakeStreamxClient = new FakeStreamxClient();
    fakeClients.put(config.getStreamxUrl(), fakeStreamxClient);
    return new StreamxInstanceClient(fakeStreamxClient, config);
  }

  public FakeStreamxClient getFakeClient(String streamxUrl) {
    return fakeClients.get(streamxUrl);
  }
}
