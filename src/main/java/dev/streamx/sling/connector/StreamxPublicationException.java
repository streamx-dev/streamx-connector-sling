package dev.streamx.sling.connector;

/**
 * The {@code StreamxPublicationException} class is a custom exception that indicates a temporary error
 * has occurred during a publication operation, and the operation should be retried.
 */
public class StreamxPublicationException extends Exception {

  public StreamxPublicationException(String message) {
    super(message);
  }

  public StreamxPublicationException(String message, Throwable cause) {
    super(message, cause);
  }
}
