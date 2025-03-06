package dev.streamx.sling.connector.handlers.resourcepath;

import java.nio.ByteBuffer;

/**
 * Represents object which is capable of being served via HTTP.
 */
public class WebResource extends Resource {

  public WebResource() {
  }

  public WebResource(ByteBuffer content) {
    super(content);
  }

  public WebResource(byte[] content) {
    super(content);
  }

  public WebResource(String content) {
    super(content);
  }

}
