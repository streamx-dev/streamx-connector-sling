package dev.streamx.sling.connector.testing.streamx.clients.ingestion;

public class Publication {

  @SuppressWarnings("unused")
  private final String action;

  @SuppressWarnings("unused")
  private final String key;

  @SuppressWarnings("unused")
  private final String channel;

  @SuppressWarnings("unused")
  private final String data;

  public Publication(String action, String key, String channel, Object data) {
    this.action = action;
    this.key = key;
    this.channel = channel;
    this.data = data != null ? data.toString() : null;
  }
}
