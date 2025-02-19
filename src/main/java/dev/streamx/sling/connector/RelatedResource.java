package dev.streamx.sling.connector;

/**
 * The {@code RelatedResource} class encapsulates information about related resources along with
 * their publication actions, indicating whether they should be published or unpublished.
 */
public class RelatedResource {

  private final String resourcePath;
  private final IngestionActionType ingestionActionType;

  /**
   * Constructs a {@code RelatedResource} object with the specified resource path and publication
   * action.
   *
   * @param resourcePath        the path of the related resource
   * @param ingestionActionType the publication action for the related resource
   */
  public RelatedResource(String resourcePath, IngestionActionType ingestionActionType) {
    this.resourcePath = resourcePath;
    this.ingestionActionType = ingestionActionType;
  }

  /**
   * Returns the path of the related resource.
   *
   * @return the resource path
   */
  public String getResourcePath() {
    return resourcePath;
  }

  /**
   * Returns the publication action for the related resource.
   *
   * @return the publication action
   */
  public IngestionActionType getIngestionActionType() {
    return ingestionActionType;
  }

  @Override
  public int hashCode() {
    return resourcePath.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof RelatedResource)) {
      return false;
    }

    if (resourcePath == null && ((RelatedResource) o).resourcePath == null
        && ingestionActionType == null
        && ((RelatedResource) o).ingestionActionType == null) {
      return true;
    }

    return resourcePath != null && resourcePath.equals(((RelatedResource) o).getResourcePath())
        && ingestionActionType
        != null && ingestionActionType.equals(((RelatedResource) o).ingestionActionType);
  }

}
