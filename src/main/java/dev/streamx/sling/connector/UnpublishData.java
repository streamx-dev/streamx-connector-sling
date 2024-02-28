package dev.streamx.sling.connector;

public class UnpublishData<T> extends PublicationData<T> {

  public UnpublishData(String key, String channel, Class<T> dataClass) {
    super(key, channel, dataClass);
  }
}
