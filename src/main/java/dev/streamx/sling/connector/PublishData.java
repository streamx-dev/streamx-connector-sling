package dev.streamx.sling.connector;

public class PublishData<T> extends PublicationData<T> {

  private final T model;

  public PublishData(String key, String channel, Class<T> modelClass, T model) {
    super(key, channel, modelClass);
    this.model = model;
  }

  public T getModel() {
    return model;
  }

}
