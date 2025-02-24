package dev.streamx.sling.connector.handling;

/**
 * Configuration for {@link InternalResourcesHandler}.
 */
@SuppressWarnings("InterfaceNeverImplemented")
public interface Configuration {

  /**
   * Returns the regex to match paths of resources that should be ingested by StreamX.
   *
   * @return regex to match paths of resources that should be ingested by StreamX
   */
  String resourcePathRegex();

  /**
   * Returns name of the channel in StreamX to publish resources to.
   *
   * @return name of the channel in StreamX to publish resources to
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
