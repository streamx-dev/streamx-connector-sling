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
   */
  void publish(List<ResourceInfo> resources);

  /**
   * Unpublishes the specified resources.
   *
   * @param resources the resources to unpublish
   */
  void unpublish(List<ResourceInfo> resources);

}
