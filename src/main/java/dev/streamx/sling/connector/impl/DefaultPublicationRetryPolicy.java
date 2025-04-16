package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.PublicationRetryPolicy;
import org.apache.sling.event.jobs.Job;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.osgi.service.metatype.annotations.Designate;

/**
 * Default {@link PublicationRetryPolicy}.
 */
@Component(service = PublicationRetryPolicy.class)
@Designate(ocd = DefaultPublicationRetryPolicyConfig.class)
@ServiceDescription("Default Publication Retry Policy")
public class DefaultPublicationRetryPolicy implements PublicationRetryPolicy {

  private int retryDelay;
  private int retryMultiplication;
  private int maxRetryDelay;

  /**
   * Constructs an instance of this class.
   */
  public DefaultPublicationRetryPolicy() {
  }

  /**
   * Configure this service.
   * @param config configuration for this service
   */
  @Activate
  @Modified
  private void configure(DefaultPublicationRetryPolicyConfig config) {
    this.retryDelay = config.retry_delay();
    this.retryMultiplication = config.retry_multiplication();
    this.maxRetryDelay = config.max_retry_delay();
  }

  @Override
  public Integer getRetryDelay(Job job) {
    int retries = job.getRetryCount();
    int calculatedDelay = (int) (retryDelay * Math.pow(retryMultiplication, retries));
    return Math.min(calculatedDelay, maxRetryDelay);
  }
}
