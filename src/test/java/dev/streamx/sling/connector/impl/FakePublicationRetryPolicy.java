package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.PublicationRetryPolicy;
import org.apache.sling.event.jobs.Job;

public class FakePublicationRetryPolicy implements PublicationRetryPolicy {

  @Override
  public Integer getRetryDelay(Job job) {
    return 1000;
  }
}
