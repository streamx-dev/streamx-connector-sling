package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.StreamxClient;
import dev.streamx.clients.ingestion.exceptions.StreamxClientException;
import dev.streamx.sling.connector.HttpClientFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = StreamxClientFactory.class)
public class StreamxClientFactoryImpl implements StreamxClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(StreamxClientFactoryImpl.class);

  @Reference(cardinality = ReferenceCardinality.OPTIONAL)
  private HttpClientFactory customHttpClientFactory;

  @Reference
  private DefaultHttpClientFactory defaultHttpClientFactory;

  public StreamxInstanceClient createStreamxClient(StreamxClientConfig config)
      throws StreamxClientException {
    CloseableHttpClient providedHttpClient = customHttpClientFactory != null
        ? customHttpClientFactory.createNewClient()
        : null;
    if (providedHttpClient != null) {
      LOG.info("Using provided HttpClient from: {}", customHttpClientFactory.getClass().getName());
      return createStreamxClient(providedHttpClient, config);
    } else {
      LOG.info("No HttpClient provided, using a default from StreamX connector");
      return createStreamxClient(defaultHttpClientFactory.createNewClient(), config);
    }
  }

  private StreamxInstanceClient createStreamxClient(CloseableHttpClient httpClient,
      StreamxClientConfig config)
      throws StreamxClientException {
    StreamxClient streamxClient = StreamxClient.builder(config.getStreamxUrl())
        .setAuthToken(!StringUtils.isBlank(config.getAuthToken()) ? config.getAuthToken() : null)
        .setApacheHttpClient(httpClient)
        .build();
    LOG.trace("Created StreamX client for: '{}'", config.getStreamxUrl());
    return new StreamxInstanceClient(streamxClient, config);
  }

}
