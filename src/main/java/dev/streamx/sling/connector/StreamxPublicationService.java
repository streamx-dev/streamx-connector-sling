package dev.streamx.sling.connector;

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
   * Publishes the specified {@link ResourceData}.
   *
   * @param resourceData {@link ResourceData} to publish
   */
  void publish(ResourceData resourceData);

  /**
   * Unpublishes the specified {@link ResourceData}.
   *
   * @param resourceData {@link ResourceData} to unpublish
   */
  void unpublish(ResourceData resourceData);

}
