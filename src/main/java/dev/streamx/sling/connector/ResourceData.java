package dev.streamx.sling.connector;

import java.util.Map;
import org.apache.sling.api.resource.Resource;

/**
 * Data that is supposed to be ingested by StreamX REST Ingestion Service.
 */
@FunctionalInterface
public interface ResourceData {

  /**
   * Equivalent of {@link Resource#getPath()} that identifies the {@link ResourceData}.
   *
   * @return equivalent of {@link Resource#getPath()} that identifies the {@link ResourceData}
   */
  String resourcePath();

  /**
   * Additional properties that are associated with the {@link ResourceData}. The returned
   * properties do not include the {@link #resourcePath()}.
   *
   * @return additional properties that are associated with the {@link ResourceData}.
   */
  default Map<String, Object> properties() {
    return Map.of();
  }
}
