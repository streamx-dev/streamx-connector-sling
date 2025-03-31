package dev.streamx.sling.connector;

/**
 * The {@code PublicationData} class represents the data required for publication,
 * including a unique key, the publication channel, and the model class type.
 *
 * @param <T> the type of the model to be published
 */
public abstract class PublicationData<T> {

  private final String key;
  private final String channel;
  private final Class<T> modelClass;

  /**
   * Constructs a {@code PublicationData}
   *
   * @param key the unique key identifying the publish data
   * @param channel the channel through which the data will be published
   * @param modelClass the class type of the model to be published
   */
  protected PublicationData(String key, String channel, Class<T> modelClass) {
    this.key = key;
    this.channel = channel;
    this.modelClass = modelClass;
  }

  /**
   * Returns the unique key identifying the publish data.
   *
   * @return unique key identifying the publish data
   */
  public String getKey() {
    return key;
  }

  /**
   * Returns the channel through which the data will be published.
   *
   * @return the channel through which the data will be published
   */
  public String getChannel() {
    return channel;
  }

  /**
   * Returns the class type of the model to be published.
   *
   * @return the class type of the model to be published
   */
  public Class<T> getModelClass() {
    return modelClass;
  }
}
