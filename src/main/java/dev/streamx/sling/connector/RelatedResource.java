package dev.streamx.sling.connector;

/**
 * The {@code RelatedResource} class encapsulates information about related resources
 * along with their publication actions, indicating whether they should be published or unpublished.
 */
public class RelatedResource extends ResourceToIngest {

  /**
   * Publication action for the related resource
   */
  private final PublicationAction action;

  /**
   * Constructs a {@code RelatedResource} object with the specified resource path and publication action.
   *
   * @param resourcePath the path of the related resource
   * @param primaryNodeType the type of the related resource
   * @param action the publication action for the related resource
   */
  public RelatedResource(String resourcePath, String primaryNodeType, PublicationAction action) {
    super(resourcePath, primaryNodeType);
    this.action = action;
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
    return getPath().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof RelatedResource)) {
      return false;
    }

    if (getPath() == null && ((RelatedResource) o).getPath() == null
        && getPrimaryNodeType() == null && ((RelatedResource) o).getPrimaryNodeType() == null
        && action == null && ((RelatedResource) o).action == null) {
      return true;
    }

    return getPath() != null && getPath().equals(((RelatedResource) o).getPath())
           && getPrimaryNodeType() != null && getPrimaryNodeType().equals(((RelatedResource) o).getPrimaryNodeType())
           && action != null && action.equals(((RelatedResource) o).action);
  }

}