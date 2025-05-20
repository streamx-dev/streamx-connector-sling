package dev.streamx.sling.connector;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Specifies path and JCR primary node type of a resource to be published or unpublished
 */
public class ResourceToIngest {

  private static ObjectMapper objectMapper;

  /**
   * Path of the resource
   */
  private final String path;

  /**
   * Type of the resource
   */
  private final String primaryNodeType;

  /**
   * Creates an instance of ResourceToIngest
   * @param path path of the resource
   * @param primaryNodeType type of the resource
   */
  @JsonCreator
  public ResourceToIngest(
      @JsonProperty("path") String path,
      @JsonProperty("primaryNodeType") String primaryNodeType) {
    this.path = path;
    this.primaryNodeType = primaryNodeType;
  }

  /**
   * @return path of the resource
   */
  public String getPath() {
    return path;
  }

  /**
   * @return type of the resource
   */
  public String getPrimaryNodeType() {
    return primaryNodeType;
  }

  /**
   * @return the current instance serialized to JSON
   */
  public String serialize() {
    try {
      return getOrCreateObjectMapper().writeValueAsString(this);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Error serializing ResourceToIngest", ex);
    }
  }

  /**
   * @param serialized an ResourceToIngest instance serialized to JSON
   * @return the JSON string deserialized to a ResourceToIngest instance
   */
  public static ResourceToIngest deserialize(String serialized) {
    try {
      return getOrCreateObjectMapper().readValue(serialized, ResourceToIngest.class);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Error deserializing ResourceToIngest", ex);
    }
  }

  private static ObjectMapper getOrCreateObjectMapper() {
    if (objectMapper == null) {
      // use lazy init, since initializing at declaration causes the following runtime problem when this class is used in OSGI context:
      //   NoClassDefFound com/fasterxml/jackson/databind/util/internal/PrivateMaxEntriesMap$Builder
      objectMapper = new ObjectMapper();
    }
    return objectMapper;
  }

  @Override
  public String toString() {
    return "Path: " + path + ", Primary Node Type: " + primaryNodeType;
  }
}
