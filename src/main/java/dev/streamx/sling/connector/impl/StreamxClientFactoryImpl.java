package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.StreamxClient;
import dev.streamx.clients.ingestion.StreamxClientBuilder;
import dev.streamx.clients.ingestion.exceptions.StreamxClientException;
import dev.streamx.sling.connector.HttpClientFactory;
import dev.streamx.sling.connector.impl.StreamxClientFactoryImpl.Config;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Designate(ocd = Config.class)
public class StreamxClientFactoryImpl implements StreamxClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(StreamxClientFactoryImpl.class);

  @Reference(cardinality = ReferenceCardinality.OPTIONAL)
  private HttpClientFactory customHttpClientFactory;

  @Reference
  private DefaultHttpClientFactory defaultHttpClientFactory;

  private Config config;

  @Activate
  private void activate(Config config) {
    this.config = config;
  }

  public StreamxClient createStreamxClient()
      throws StreamxClientException {
    CloseableHttpClient providedHttpClient = customHttpClientFactory != null
        ? customHttpClientFactory.createNewClient()
        : null;
    if (providedHttpClient != null) {
      LOG.info("Using provided HttpClient from: {}", customHttpClientFactory.getClass().getName());
      return createStreamxClient(providedHttpClient);
    } else {
      LOG.info("No HttpClient provided, using a default from StreamX connector");
      return createStreamxClient(defaultHttpClientFactory.createNewClient());
    }
  }

  private StreamxClient createStreamxClient(CloseableHttpClient httpClient)
      throws StreamxClientException {
    return StreamxClient.builder(config.streamxUrl())
        .setAuthToken(!StringUtils.isBlank(config.authToken()) ? config.authToken() : null)
        .setApacheHttpClient(httpClient)
        .build();
  }

  @ObjectClassDefinition(name = "StreamX Connector Configuration")
  @interface Config {

    @AttributeDefinition(name = "URL to StreamX", description =
        "URL to StreamX instance that will receive publication requests.")
    String streamxUrl();

    @AttributeDefinition(name = "JWT", description =
        "JWT that will be sent by during publication requests.")
    String authToken();
  }

}
