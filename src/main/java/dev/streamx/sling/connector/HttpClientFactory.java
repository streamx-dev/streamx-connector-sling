package dev.streamx.sling.connector;

import org.apache.http.impl.client.CloseableHttpClient;

/**
 * The {@code HttpClientFactory} interface defines a method for creating new instances of {@link CloseableHttpClient}.
 * Implementations of this interface should provide the logic to configure and instantiate {@link CloseableHttpClient} objects.
 */
public interface HttpClientFactory {

  /**
   *
   * @return a new {@link CloseableHttpClient} instance
   */
  CloseableHttpClient createNewClient();

}
