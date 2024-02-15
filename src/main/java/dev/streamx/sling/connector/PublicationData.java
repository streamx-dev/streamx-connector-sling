package dev.streamx.sling.connector;

public abstract class PublicationData<T> {

  private final String key;

  private final String channel;

  private final Class<T> modelClass;

  protected PublicationData(String key, String channel, Class<T> modelClass) {
    this.key = key;
    this.channel = channel;
    this.modelClass = modelClass;
  }

  public String getKey() {
    return key;
  }

  public String getChannel() {
    return channel;
  }

  public Class<T> getModelClass() {
    return modelClass;
  }
}
