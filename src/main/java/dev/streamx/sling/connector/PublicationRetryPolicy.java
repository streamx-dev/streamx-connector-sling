package dev.streamx.sling.connector;

import org.apache.sling.event.jobs.Job;

/**
 * The {@code PublicationRetryPolicy} interface provides a method for calculating the retry delay
 * for a given job. Implementations of this interface should determine the logic to compute the
 * delay in milliseconds before the next attempt to publish the job after a failure.
 */
public interface PublicationRetryPolicy {

  /**
   * Calculates the delay in milliseconds before retrying the publication of the specified job
   * after a failure.
   *
   * @param job the job for which the retry delay is to be calculated
   * @return the retry delay in milliseconds
   */
  Integer getRetryDelay(Job job);

}