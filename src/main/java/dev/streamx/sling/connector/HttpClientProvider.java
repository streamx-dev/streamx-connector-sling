package dev.streamx.sling.connector;

import org.apache.http.impl.client.CloseableHttpClient;

public interface HttpClientProvider {

  CloseableHttpClient getClient();

}
