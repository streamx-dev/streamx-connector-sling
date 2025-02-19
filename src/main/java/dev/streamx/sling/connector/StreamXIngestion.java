package dev.streamx.sling.connector;

import java.util.List;

/**
 * StreamX REST Ingestion Service.
 */
public interface StreamXIngestion {

  /**
   * Indicates whether this service is enabled and must undertake the supposed actions or not.
   *
   * @return {@code true} if this service is enabled, {@code false} otherwise
   */
  boolean isEnabled();

  /**
   * Publishes data to StreamX.
   *
   * @param keys identifiers of the data that should be published to StreamX
   * @throws StreamxPublicationException if a temporary error has occurred during a publication
   *                                     operation, and the operation should be retried
   */
  void publish(List<String> keys) throws StreamxPublicationException;

  /**
   * Unpublishes data from StreamX.
   *
   * @param keys identifiers of the data that should be unpublished from StreamX
   * @throws StreamxPublicationException if a temporary error has occurred during an unpublishing
   *                                     operation, and the operation should be retried
   */
  void unpublish(List<String> keys) throws StreamxPublicationException;

}
