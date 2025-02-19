package dev.streamx.sling.connector;

/**
 * Piece of data ingested by {@link StreamXIngestion}.
 *
 * @param <T> type modelling the data ingested by StreamX
 */
public interface IngestionData<T> {

  /**
   * Returns the identifier of the data ingested by StreamX.
   *
   * @return identifier of the data ingested by StreamX
   */
  IngestionDataKey key();

  /**
   * Returns the {@link StreamXChannel} through which the data is supposed to be ingested by
   * StreamX.
   *
   * @return {@link StreamXChannel} through which the data is supposed to be ingested by StreamX
   */
  StreamXChannel channel();

  /**
   * Returns the type modelling the data ingested by StreamX.
   *
   * @return type modelling the data ingested by StreamX
   */
  Class<T> modelClass();
}
