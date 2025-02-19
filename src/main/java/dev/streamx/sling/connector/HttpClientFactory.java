package dev.streamx.sling.connector;

import org.apache.http.impl.client.CloseableHttpClient;

/**
 * Factory that produces instances of {@link CloseableHttpClient}.
 */
@FunctionalInterface
public interface HttpClientFactory {

  /**
   * Creates a new instance of {@link CloseableHttpClient}.
   *
   * @return a new instance of {@link CloseableHttpClient}
   */
  CloseableHttpClient createNewClient();

}
