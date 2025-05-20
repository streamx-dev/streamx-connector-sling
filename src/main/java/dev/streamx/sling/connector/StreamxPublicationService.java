package dev.streamx.sling.connector;

import java.util.List;

/**
 * Service for triggering ingestion operations into StreamX REST Ingestion Service.
 */
public interface StreamxPublicationService {

  /**
   * Indicates whether the service is enabled or not.
   *
   * @return {@code true} if the service is enabled; {@code false} otherwise
   */
  boolean isEnabled();

  /**
   * Publishes the specified resources.
   *
   * @param resources the resources to publish
   * @throws StreamxPublicationException if a temporary error occurs and the operation should be
   *                                     retried
   */
  void publish(List<ResourceToIngest> resources) throws StreamxPublicationException;

  /**
   * Unpublishes the specified resources.
   *
   * @param resources the resources to unpublish
   * @throws StreamxPublicationException if a temporary error occurs and the operation should be
   *                                     retried
   */
  void unpublish(List<ResourceToIngest> resources) throws StreamxPublicationException;

}
