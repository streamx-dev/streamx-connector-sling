package dev.streamx.sling.connector;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;

/**
 * Specifies path and JCR primary node type of a resource to be published or unpublished
 */
public class ResourceInfo {

  private static final String FIELDS_SEPARATOR = "`@`";

  /**
   * Path of the resource
   */
  private final String path;

  /**
   * Primary node type of the resource
   */
  private final String primaryNodeType;

  /**
   * Creates an instance of {@link ResourceInfo}
   * @param path path of the resource
   * @param primaryNodeType primary node type of the resource
   */
  public ResourceInfo(String path, String primaryNodeType) {
    this.path = requireNotBlank("path", path);
    this.primaryNodeType = requireNotBlank("primaryNodeType", primaryNodeType);
  }

  private static String requireNotBlank(String fieldName, String fieldValue) {
    if (StringUtils.isBlank(fieldValue)) {
      throw new IllegalArgumentException(fieldName + " cannot be blank");
    }
    return fieldValue;
  }

  /**
   * @return path of the resource
   */
  public String getPath() {
    return path;
  }

  /**
   * @return primary node type of the resource
   */
  public String getPrimaryNodeType() {
    return primaryNodeType;
  }

  /**
   * @return the current instance serialized to JSON
   */
  public String serialize() {
    return path + FIELDS_SEPARATOR + primaryNodeType;
  }

  /**
   * @param serialized a {@link ResourceInfo} instance serialized to JSON
   * @return the JSON string deserialized to a {@link ResourceInfo} instance
   */
  public static ResourceInfo deserialize(String serialized) {
    if (serialized != null) {
      String[] parts = serialized.split(FIELDS_SEPARATOR);
      if (parts.length == 2) {
        return new ResourceInfo(parts[0], parts[1]);
      }
    }
    throw new IllegalArgumentException("Error deserializing " + serialized + "to " + ResourceInfo.class);
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
