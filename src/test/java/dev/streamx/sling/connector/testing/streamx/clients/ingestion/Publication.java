package dev.streamx.sling.connector.testing.streamx.clients.ingestion;

import dev.streamx.sling.connector.PublicationAction;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class Publication {

  private final PublicationAction action;
  private final String key;
  private final String channel;
  private final String data;

  public Publication(PublicationAction action, String key, String channel, Object data) {
    this.action = action;
    this.key = key;
    this.channel = channel;
    this.data = data != null ? data.toString() : null;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
        .append("action", action)
        .append("key", key)
        .append("channel", channel)
        .append("data", data)
        .toString();
  }
}
