package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.PublicationRetryPolicy;
import org.apache.sling.event.jobs.Job;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

@Component(service = PublicationRetryPolicy.class)
@Designate(ocd = DefaultPublicationRetryPolicyConfig.class)
public class DefaultPublicationRetryPolicy implements PublicationRetryPolicy {

  private int retryDelay;
  private int retryMultiplication;
  private int maxRetryDelay;

  @Activate
  @Modified
  private void activate(DefaultPublicationRetryPolicyConfig config) {
    this.retryDelay = config.retry_delay();
    this.retryMultiplication = config.retry_multiplication();
    this.maxRetryDelay = config.max_retry_delay();
  }

  @Override
  public Integer getRetryDelay(Job job) {
    int retries = job.getRetryCount() + 1;
    int calculatedDelay = (int) (retryDelay * Math.pow(retryMultiplication, retries));
    return Math.min(calculatedDelay, maxRetryDelay);
  }
}
