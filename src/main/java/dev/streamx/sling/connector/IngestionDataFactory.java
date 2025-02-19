package dev.streamx.sling.connector;

/**
 * Produces {@link IngestionData}.
 *
 * @param <T> type modelling the data ingested by StreamX
 */
public interface IngestionDataFactory<T> {

  /**
   * Returns the identifier of this service.
   *
   * @return identifier of this service
   */
  String getId();

  /**
   * Indicates whether this service can produce {@link IngestionData} for the given key.
   *
   * @param ingestionDataKey identifier of the data ingested by StreamX
   * @return whether this factory can produce {@link IngestionData} for the given key
   */
  boolean canProduce(IngestionDataKey ingestionDataKey);

  PublishData<T> producePublishData(IngestionDataKey ingestionDataKey)
      throws StreamxPublicationException;

  UnpublishData<T> produceUnpublishData(IngestionDataKey ingestionDataKey)
      throws StreamxPublicationException;

}
