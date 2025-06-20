package dev.streamx.sling.connector;

import java.util.Collection;

/**
 * The {@code RelatedResourcesSelector} interface defines a method for selecting related resources
 * based on a given resource path.
 * Implementations of this interface should provide the logic to determine which resources are
 * related and need to be handled together for publishing.
 */
public interface RelatedResourcesSelector {

  /**
   * Retrieves a collection of related resources based on the specified resource path.
   *
   * @param resourcePath the path of the resource for which related resources are to be selected
   * @return a collection of resource paths that are related to the specified resource
   */
  Collection<String> getRelatedResources(String resourcePath);

  /**
   * Removes all instances of {@code parentResourcePaths} from {@code relatedResourcePaths}.
   * @param relatedResourcePaths collection of related resource paths found by {@link #getRelatedResources(String resourcePath)}
   * @param parentResourcePaths collection of source resource paths, for which the {@code relatedResourcePaths} were found
   */
  void removeParentResources(Collection<String> relatedResourcePaths, Collection<String> parentResourcePaths);

}
