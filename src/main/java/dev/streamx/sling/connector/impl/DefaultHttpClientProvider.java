package dev.streamx.sling.connector.impl;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = DefaultHttpClientProvider.class)
@Designate(ocd = HttpClientProviderConfig.class)
public class DefaultHttpClientProvider {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpClientProvider.class);

  @Reference
  private HttpClientBuilderFactory httpClientBuilderFactory;

  private CloseableHttpClient httpClient;

  @Activate
  @Modified
  private void activate(HttpClientProviderConfig config) {
    PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager();
    connMgr.setMaxTotal(config.max_total());
    connMgr.setDefaultMaxPerRoute(config.max_per_route());
    httpClient = httpClientBuilderFactory.newBuilder()
        .setConnectionManager(connMgr)
        .setKeepAliveStrategy((response, context) -> config.keep_alive_time())
        .evictIdleConnections(config.idle_connection_keep_alive_time(), TimeUnit.MILLISECONDS)
        .evictExpiredConnections()
        .setDefaultRequestConfig(RequestConfig.custom()
            .setConnectTimeout(config.connection_timeout())
            .setSocketTimeout(config.socket_timeout())
            .setConnectionRequestTimeout(config.connection_request_timeout())
            .build())
        .build();
  }

  @Deactivate
  private void deactivate() {
    if (httpClient != null) {
      try {
        httpClient.close();
      } catch (final IOException e) {
        LOG.debug("Error during closing HTTP client", e);
      }
      httpClient = null;
    }
  }

  public CloseableHttpClient getClient() {
    return httpClient;
  }

}
