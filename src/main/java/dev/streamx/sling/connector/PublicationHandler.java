package dev.streamx.sling.connector;

/**
 * The {@code PublicationHandler} interface defines the contract for handling the publication and
 * unpublication of resources.
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
   * Indicates whether this handler can handle the specified {@link ResourceData}.
   *
   * @param resourceData {@link ResourceData} that is supposed to be handled
   * @return {@code true} if this handler can handle the {@link ResourceData}; {@code false}
   * otherwise
   */
  boolean canHandle(ResourceData resourceData);

  /**
   * Prepares the data to publish for the specified {@link ResourceData}. May return {@code null} if
   * nothing to publish.
   *
   * @param resourceData {@link ResourceData} that is supposed to be published
   * @return {@link PublishData} data for the specified {@link ResourceData}, may be {@code null}
   * @throws StreamxPublicationException if a temporary error occurs and the operation should be
   *                                     retried
   */
  PublishData<T> getPublishData(ResourceData resourceData) throws StreamxPublicationException;

  /**
   * Prepares the data to unpublish for the specified {@link ResourceData}. May return {@code null}
   * if nothing to unpublish.
   *
   * @param resourceData {@link ResourceData} that is supposed to be unpublished
   * @return {@link UnpublishData} data for the specified {@link ResourceData}, may be {@code null}
   * @throws StreamxPublicationException if a temporary error occurs and the operation should be
   *                                     retried
   */
  UnpublishData<T> getUnpublishData(ResourceData resourceData) throws StreamxPublicationException;

}
