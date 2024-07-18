package dev.streamx.sling.connector.testing.sling.event.jobs;

import java.util.Map;

public class FakeRetriedJob extends FakeJob {

  private final int retries;

  public FakeRetriedJob(String topic, Map<String, Object> properties, int retries) {
    super(topic, properties);
    this.retries = retries;
  }
  @Override
  public int getRetryCount() {
    return retries;
  }
}
