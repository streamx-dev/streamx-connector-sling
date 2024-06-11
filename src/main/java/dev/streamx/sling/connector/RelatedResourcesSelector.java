package dev.streamx.sling.connector;

import java.util.Collection;

/**
 * The {@code RelatedResourcesSelector} interface defines a method for selecting related resources
 * based on a given resource path and publication action.
 * Implementations of this interface should provide the logic to determine which resources are
 * related and need to be handled together for publication actions.
 */
public interface RelatedResourcesSelector {

  /**
   * Retrieves a collection of related resources based on the specified resource path and publication action.
   *
   * @param resourcePath the path of the resource for which related resources are to be selected
   * @param action the publication action to be applied to the related resources
   * @return a collection of {@code RelatedResource} objects that are related to the specified resource
   * @throws StreamxPublicationException if a temporary error occurs and the operation should be retried
   */
  Collection<RelatedResource> getRelatedResources(String resourcePath, PublicationAction action)
      throws StreamxPublicationException;

}
