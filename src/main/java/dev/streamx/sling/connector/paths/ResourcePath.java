package dev.streamx.sling.connector.paths;

import org.apache.sling.api.resource.Resource;

/**
 * Path to a {@link Resource}.
 */
class ResourcePath {

  private final String value;

  ResourcePath(String value) {
    this.value = value;
  }

  String get() {
    return value;
  }

  boolean matches(String regex) {
    return value.matches(regex);
  }

  @Override
  public String toString() {
    return "ResourcePath{" +
        "value='" + value + '\'' +
        '}';
  }
}
