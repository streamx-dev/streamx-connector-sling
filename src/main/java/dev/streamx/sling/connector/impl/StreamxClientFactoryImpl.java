package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.StreamxClient;
import dev.streamx.clients.ingestion.exceptions.StreamxClientException;
import dev.streamx.sling.connector.HttpClientProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class StreamxClientFactoryImpl implements StreamxClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(StreamxClientFactoryImpl.class);

  @Reference(cardinality = ReferenceCardinality.OPTIONAL)
  private HttpClientProvider customHttpClientProvider;

  @Reference
  private DefaultHttpClientProvider defaultHttpClientProvider;

  public StreamxClient createStreamxClient(String streamxUrl, String authToken)
      throws StreamxClientException {
    CloseableHttpClient providedHttpClient = customHttpClientProvider != null
        ? customHttpClientProvider.getClient()
        : null;
    if (providedHttpClient != null) {
      LOG.info("Using provided HttpClient from: {}", customHttpClientProvider.getClass().getName());
      return createStreamxClient(streamxUrl, authToken, providedHttpClient);
    } else {
      LOG.info("No HttpClient provided, using a default from StreamX connector");
      return createStreamxClient(streamxUrl, authToken, defaultHttpClientProvider.getClient());
    }
  }

  private StreamxClient createStreamxClient(String streamxUrl, String authToken,
      CloseableHttpClient httpClient) throws StreamxClientException {
    return StreamxClient.builder(streamxUrl)
        .setAuthToken(authToken)
        .setApacheHttpClient(httpClient)
        .build();
  }

}
