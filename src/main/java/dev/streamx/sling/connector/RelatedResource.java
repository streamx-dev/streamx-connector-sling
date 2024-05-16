package dev.streamx.sling.connector;

public class RelatedResource {

  private final String resourcePath;
  private final PublicationAction action;

  public RelatedResource(String resourcePath, PublicationAction action) {
    this.resourcePath = resourcePath;
    this.action = action;
  }

  public String getResourcePath() {
    return resourcePath;
  }

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
