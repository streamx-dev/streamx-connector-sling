package dev.streamx.sling.connector;

/**
 * The {@code RelatedResource} class encapsulates information about related resources.
 */
public class RelatedResource extends ResourceInfo {

  /**
   * Constructs a {@code RelatedResource} object with the specified resource path and primary node type.
   *
   * @param resourcePath the path of the related resource
   * @param primaryNodeType the type of the related resource
   */
  public RelatedResource(String resourcePath, String primaryNodeType) {
    super(resourcePath, primaryNodeType);
  }

  @Override
  public int hashCode() {
    return getPath().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof RelatedResource)) {
      return false;
    }

    if (getPath() == null && ((RelatedResource) o).getPath() == null
        && getPrimaryNodeType() == null && ((RelatedResource) o).getPrimaryNodeType() == null) {
      return true;
    }

    return getPath() != null && getPath().equals(((RelatedResource) o).getPath())
           && getPrimaryNodeType() != null && getPrimaryNodeType().equals(((RelatedResource) o).getPrimaryNodeType());
  }

}