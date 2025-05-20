package dev.streamx.sling.connector;

/**
 * The {@code PublicationHandler} interface defines the contract for handling
 * the publication and unpublication of resources.
 *
 * @param <T> the type of data to be published
 */
public interface PublicationHandler<T> {

  /**
   * Returns the unique identifier for this handler.
   *
   * @return the unique identifier for this handler
   */
  String getId();

  /**
   * Determines if this handler can handle the specified resource path.
   *
   * @param resource the path and type of the resource
   * @return {@code true} if this handler can handle the resource path, {@code false} otherwise
   */
  boolean canHandle(ResourceToIngest resource);

  /**
   * Prepares the data to publish for the specified resource path. 
   * May return null if nothing to publish.
   *
   * @param resourcePath the path of the resource
   * @return the publish data for the resource, may be null
   * @throws StreamxPublicationException if a temporary error occurs and the operation should be retried
   */
  PublishData<T> getPublishData(String resourcePath) throws StreamxPublicationException;

  /**
   * Prepares the data to unpublish for the specified resource path. 
   * May return null if nothing to unpublish.
   *
   * @param resourcePath the path of the resource
   * @return the unpublish data for the resource, may be null
   * @throws StreamxPublicationException if a temporary error occurs and the operation should be retried
   */
  UnpublishData<T> getUnpublishData(String resourcePath) throws StreamxPublicationException;

}
