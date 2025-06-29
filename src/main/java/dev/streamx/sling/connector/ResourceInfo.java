package dev.streamx.sling.connector;

import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.jetbrains.annotations.Nullable;

/**
 * Specifies path and JCR primary node type of a resource to be published or unpublished
 */
public class ResourceInfo {

  private static ObjectMapper objectMapper;

  /**
   * Path of the resource
   */
  private final String path;

  /**
   * Primary node type of the resource. Null for non-JCR resources
   */
  @Nullable
  private final String primaryNodeType;

  /**
   * Creates an instance of {@link ResourceInfo} with null value for primaryNodeType
   * @param path path of the resource
   */
  public ResourceInfo(String path) {
    this(path, null);
  }

  /**
   * Creates an instance of {@link ResourceInfo}
   * @param path path of the resource
   * @param primaryNodeType primary node type of the resource. Null for non-JCR resources
   */
  @JsonCreator
  public ResourceInfo(
      @JsonProperty("path") String path,
      @JsonProperty("primaryNodeType") @Nullable String primaryNodeType) {
    if (StringUtils.isBlank(path)) {
      throw new IllegalArgumentException("path cannot be blank");
    }
    this.path = path;
    this.primaryNodeType = primaryNodeType;
  }

  /**
   * Returns path of the resource
   * @return path of the resource
   */
  public String getPath() {
    return path;
  }

  /**
   * Returns primary node type of the resource
   * @return primary node type of the resource
   */
  @Nullable
  public String getPrimaryNodeType() {
    return primaryNodeType;
  }

  /**
   * Returns the current instance serialized to JSON
   * @return the current instance serialized to JSON
   */
  public String serialize() {
    try {
      return getOrCreateObjectMapper().writeValueAsString(this);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Error serializing from " + ResourceInfo.class, ex);
    }
  }

  /**
   * Returns the JSON string deserialized to a {@link ResourceInfo} instance
   * @param serialized a {@link ResourceInfo} instance serialized to JSON
   * @return the JSON string deserialized to a {@link ResourceInfo} instance
   */
  public static ResourceInfo deserialize(String serialized) {
    try {
      return getOrCreateObjectMapper().readValue(serialized, ResourceInfo.class);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Error deserializing to " + ResourceInfo.class, ex);
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
  public int hashCode() {
    return getPath().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ResourceInfo) {
      ResourceInfo that = (ResourceInfo) o;
      return new EqualsBuilder()
          .append(path, that.path)
          .append(primaryNodeType, that.primaryNodeType)
          .isEquals();
    }
    return false;
  }

  @Override
  public String toString() {
    return serialize();
  }
}
