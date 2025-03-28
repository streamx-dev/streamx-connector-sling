package dev.streamx.sling.connector.handlers.resourcepath;

import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.api.resource.Resource;

/**
 * Configuration for {@link ResourcePathPublicationHandler}.
 */
public interface ResourcePathPublicationHandlerConfig {

  /**
   * Returns the regex to match {@link SlingUri}s of {@link Resource}s that should be ingested by
   * StreamX.
   *
   * @return regex to match {@link SlingUri}s of {@link Resource}s that should be ingested by
   * StreamX
   */
  String slingUriRegex();

  /**
   * Returns name of the channel in StreamX to publish {@link Resource}s to.
   *
   * @return name of the channel in StreamX to publish {@link Resource}s to
   */
  String channel();

  /**
   * Indicates whether the configured service is enabled and must undertake the supposed actions or
   * not.
   *
   * @return {@code true} if the service is enabled, {@code false} otherwise
   */
  boolean isEnabled();
}
