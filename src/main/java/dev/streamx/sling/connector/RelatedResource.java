package dev.streamx.sling.connector;

/**
 * The {@code RelatedResource} class encapsulates information about related resources
 * along with their publication actions, indicating whether they should be published or unpublished.
 */
public class RelatedResource {

  private final String resourcePath;
  private final PublicationAction action;

  /**
   * Constructs a {@code RelatedResource} object with the specified resource path and publication action.
   *
   * @param resourcePath the path of the related resource
   * @param action the publication action for the related resource
   */
  public RelatedResource(String resourcePath, PublicationAction action) {
    this.resourcePath = resourcePath;
    this.action = action;
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
  public PublicationAction getAction() {
    return action;
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

    if (resourcePath == null && ((RelatedResource) o).resourcePath == null && action == null
        && ((RelatedResource) o).action == null) {
      return true;
    }

    return resourcePath != null && resourcePath.equals(((RelatedResource) o).getResourcePath())
        && action != null && action.equals(((RelatedResource) o).action);
  }

}