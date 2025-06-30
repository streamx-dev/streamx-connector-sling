package dev.streamx.sling.connector;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collections;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.jackrabbit.JcrConstants;
import org.jetbrains.annotations.Nullable;

/**
 * Specifies path and selected JCR properties of a resource to be published or unpublished
 */
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE)
public class ResourceInfo {

  private static ObjectMapper objectMapper;

  private static final TypeReference<Map<String, String>> propertiesMapReference = new TypeReference<>() {
  };

  /**
   * Path of the resource
   */
  private final String path;

  private final Map<String, String> properties;

  /**
   * Creates an instance of {@link ResourceInfo} with empty properties map
   * @param path path of the resource
   */
  public ResourceInfo(String path) {
    this(path, Collections.emptyMap());
  }

  /**
   * Creates an instance of {@link ResourceInfo}
   * @param path path of the resource
   * @param properties resource properties
   */
  @JsonCreator
  public ResourceInfo(
      @JsonProperty("path") String path,
      @JsonProperty("properties") Map<String, String> properties) {
    if (StringUtils.isBlank(path)) {
      throw new IllegalArgumentException("path cannot be blank");
    }
    this.path = path;
    this.properties = Collections.unmodifiableMap(properties);
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
    return properties.get(JcrConstants.JCR_PRIMARYTYPE);
  }

  /**
   * Returns properties of the resource
   * @return properties of the resource
   */
  public Map<String, String> getProperties() {
    return properties;
  }

  /**
   * Returns properties serialized to JSON
   * @return properties serialized to JSON
   */
  public String getSerializedProperties() {
    try {
      return getOrCreateObjectMapper().writeValueAsString(properties);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Error serializing properties map", ex);
    }
  }

  /**
   * Returns the JSON string deserialized to a {@code Map<String, String>}
   * @param serializedProperties a {@code Map<String, String>} serialized to JSON
   * @return the JSON string deserialized to a {@code Map<String, String>}
   */
  public static Map<String, String> deserializeProperties(String serializedProperties) {
    try {
      return getOrCreateObjectMapper().readValue(serializedProperties, propertiesMapReference);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Error deserializing to properties map", ex);
    }
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
          .append(properties, that.properties)
          .isEquals();
    }
    return false;
  }

  @Override
  public String toString() {
    return serialize();
  }
}
