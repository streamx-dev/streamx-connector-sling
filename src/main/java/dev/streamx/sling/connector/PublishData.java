package dev.streamx.sling.connector;

import dev.streamx.clients.ingestion.publisher.Message;
import java.util.Map;

/**
 * The {@code PublishData} class encapsulates the data required to publish a resource.
 *
 * @param <T> the type of the data
 */
public class PublishData<T> extends PublicationData<T> {

  private final T model;

  /**
   * Constructs a new {@code PublishData} instance.
   *
   * @param key        the key identifying the publish data
   * @param channel    the channel through which the data will be published
   * @param modelClass the class type of the model to be published
   * @param model      the instance of the model to be published
   */
  public PublishData(String key, String channel, Class<T> modelClass, T model) {
    super(key, channel, modelClass);
    this.model = model;
  }

  /**
   * Constructs a new {@code PublishData} instance.
   *
   * @param key        the unique key identifying the publish data
   * @param channel    the channel through which the data will be published
   * @param modelClass the class type of the model to be published
   * @param model      the instance of the model to be published
   * @param properties value for {@link Message#getProperties()}
   */
  public PublishData(String key, String channel, Class<T> modelClass, T model, Map<String, String> properties) {
    super(key, channel, modelClass, properties);
    this.model = model;
  }

  /**
   * Returns the model to be published.
   *
   * @return the model to be published
   */
  public T getModel() {
    return model;
  }

}
