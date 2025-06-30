package dev.streamx.sling.connector;

import java.util.Collection;

/**
 * The {@code RelatedResourcesSelector} interface defines a method for selecting related resources
 * based on a given resource.
 * Implementations of this interface should provide the logic to determine which resources are
 * related and need to be handled together for publishing.
 */
public interface RelatedResourcesSelector {

  /**
   * Retrieves a collection of related resources based on the specified resource.
   *
   * @param resourceInfo information about the resource for which related resources are to be selected
   * @return a collection of {@code ResourceInfo} objects that are related to the specified resource
   */
  Collection<ResourceInfo> getRelatedResources(ResourceInfo resourceInfo);

}
