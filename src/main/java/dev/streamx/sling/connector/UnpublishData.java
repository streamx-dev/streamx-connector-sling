package dev.streamx.sling.connector;

/**
 * {@link IngestionData} for {@link IngestionActionType#UNPUBLISH}.
 */
public class UnpublishData<T> implements IngestionData<T> {

  private final IngestionDataKey ingestionDataKey;
  private final StreamXChannel channel;
  private final Class<T> modelClass;

  /**
   * Constructs a new instance of this class.
   *
   * @param ingestionDataKey {@link IngestionDataKey}
   * @param channel          {@link StreamXChannel} through which the data is supposed to be
   *                         ingested by StreamX
   * @param modelClass       type modelling the data ingested by StreamX
   */
  public UnpublishData(
      IngestionDataKey ingestionDataKey, StreamXChannel channel, Class<T> modelClass
  ) {
    this.ingestionDataKey = ingestionDataKey;
    this.channel = channel;
    this.modelClass = modelClass;
  }

  @Override
  public IngestionDataKey key() {
    return ingestionDataKey;
  }

  @Override
  public StreamXChannel channel() {
    return channel;
  }

  @Override
  public Class<T> modelClass() {
    return modelClass;
  }
}
