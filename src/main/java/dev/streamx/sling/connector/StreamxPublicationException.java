package dev.streamx.sling.connector;

/**
 * The {@code StreamxPublicationException} class is a custom exception that indicates a temporary error
 * has occurred during a publication operation, and the operation should be retried.
 */
public class StreamxPublicationException extends Exception {

  /**
   * Constructs an instance of this class.
   *
   * @param message argument for {@link Exception#Exception(String)}
   */
  public StreamxPublicationException(String message) {
    super(message);
  }

  /**
   * Constructs an instance of this class.
   *
   * @param message argument for {@link Exception#Exception(String, Throwable)}
   * @param cause   argument for {@link Exception#Exception(String, Throwable)}
   */
  public StreamxPublicationException(String message, Throwable cause) {
    super(message, cause);
  }
}
