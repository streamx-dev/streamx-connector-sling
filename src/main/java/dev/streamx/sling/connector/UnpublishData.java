package dev.streamx.sling.connector;

import dev.streamx.clients.ingestion.publisher.Message;
import java.util.Map;

/**
 * The {@code UnpublishData} class encapsulates the data required to unpublish a resource.
 *
 * @param <T> the type of the data
 */
public class UnpublishData<T> extends PublicationData<T> {

  /**
   * Constructs a new {@code UnpublishData} instance.
   *
   * @param key       the key identifying the publish data
   * @param channel   the channel through which the data will be unpublished
   * @param dataClass the class type of the model to be unpublished
   */
  public UnpublishData(String key, String channel, Class<T> dataClass) {
    super(key, channel, dataClass);
  }

  /**
   * Constructs a new {@code UnpublishData} instance.
   *
   * @param key        the key identifying the publish data
   * @param channel    the channel through which the data will be unpublished
   * @param dataClass  the class type of the model to be unpublished
   * @param properties value for {@link Message#getProperties()}
   */
  public UnpublishData(
      String key, String channel, Class<T> dataClass, Map<String, String> properties
  ) {
    super(key, channel, dataClass, properties);
  }
}
