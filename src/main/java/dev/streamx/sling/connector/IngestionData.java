package dev.streamx.sling.connector;

/**
 * Piece of data ingested by {@link StreamXIngestion}.
 *
 * @param <T> type modelling the data ingested by StreamX
 */
public abstract class IngestionData<T> {

  private final String key;
  private final String channelName;
  private final Class<T> modelClass;

  /**
   * Constructs a new instance of this class.
   *
   * @param key         identifier of the data that ingested by StreamX
   * @param channelName name of the channel through which the data ingested by StreamX
   * @param modelClass  type modelling the data ingested by StreamX
   */
  protected IngestionData(String key, String channelName, Class<T> modelClass) {
    this.key = key;
    this.channelName = channelName;
    this.modelClass = modelClass;
  }

  /**
   * Returns the identifier of the data that ingested by StreamX.
   *
   * @return identifier of the data that ingested by StreamX
   */
  public String getKey() {
    return key;
  }

  /**
   * Returns the name of the channel through which the data ingested by StreamX.
   *
   * @return name of the channel through which the data ingested by StreamX
   */
  public String getChannelName() {
    return channelName;
  }

  /**
   * Returns the type modelling the data ingested by StreamX.
   *
   * @return type modelling the data ingested by StreamX
   */
  public Class<T> getModelClass() {
    return modelClass;
  }
}
