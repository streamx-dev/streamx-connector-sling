package dev.streamx.sling.connector;

/**
 * The {@code UnpublishData} class encapsulates the data required to unpublish a resource.
 *
 * @param <T> the type of the data
 */
public class UnpublishData<T> extends IngestionData<T> {

  /**
   * Constructs a new {@code UnpublishData} instance.
   *
   * @param key the unique key identifying the publish data
   * @param channel the channel through which the data will be unpublished
   * @param dataClass the class type of the model to be unpublished
   */
  public UnpublishData(String key, String channel, Class<T> dataClass) {
    super(key, channel, dataClass);
  }
}
