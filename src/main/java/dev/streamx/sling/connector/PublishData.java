package dev.streamx.sling.connector;

/**
 * {@link IngestionData} for {@link IngestionActionType#PUBLISH}.
 */
public class PublishData<T> implements IngestionData<T> {

  private final IngestionDataKey ingestionDataKey;
  private final StreamXChannel channel;
  private final Class<T> modelClass;
  private final T model;

  /**
   * Constructs a new instance of this class.
   *
   * @param ingestionDataKey {@link IngestionDataKey}
   * @param channel          {@link StreamXChannel} through which the data is supposed to be
   *                         ingested by StreamX
   * @param modelClass       type modelling the data ingested by StreamX
   * @param model            instance of the type modelling the data ingested by StreamX
   */
  public PublishData(
      IngestionDataKey ingestionDataKey, StreamXChannel channel, Class<T> modelClass, T model
  ) {
    this.ingestionDataKey = ingestionDataKey;
    this.channel = channel;
    this.modelClass = modelClass;
    this.model = model;
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

  /**
   * Returns the instance of the type modelling the data ingested by StreamX.
   *
   * @return instance of the type modelling the data ingested by StreamX
   */
  public T model() {
    return model;
  }
}
