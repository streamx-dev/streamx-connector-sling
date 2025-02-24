package dev.streamx.sling.connector.paths;

import org.apache.sling.api.resource.Resource;

/**
 * Path to a {@link Resource} that can be represented as a text.
 */
class PathToTextResource {

  private final ResourcePath resourcePath;

  PathToTextResource(ResourcePath resourcePath) {
    this.resourcePath = resourcePath;
  }

  String get() {
    return resourcePath.get();
  }

  public ResourcePath getResourcePath() {
    return resourcePath;
  }

  @Override
  public String toString() {
    return "PathToTextResource{" +
        "resourcePath=" + resourcePath +
        '}';
  }
}
