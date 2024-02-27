package dev.streamx.sling.connector;

public class StreamxPublicationException extends Exception {

  public StreamxPublicationException(String message) {
    super(message);
  }

  public StreamxPublicationException(String message, Throwable cause) {
    super(message, cause);
  }
}
