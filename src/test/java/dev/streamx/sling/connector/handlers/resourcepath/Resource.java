package dev.streamx.sling.connector.handlers.resourcepath;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;

/**
 * Represents object containing content.
 */
public class Resource {

  private ByteBuffer content;

  public Resource() {
  }

  public Resource(ByteBuffer content) {
    this.content = content;
  }

  public Resource(byte[] content) {
    this(ByteBuffer.wrap(content));
  }

  public Resource(String content) {
    this(content.getBytes(UTF_8));
  }

  public ByteBuffer getContent() {
    return content;
  }
}
