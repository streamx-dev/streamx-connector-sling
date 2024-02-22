package dev.streamx.sling.connector.impl;

import java.util.concurrent.TimeUnit;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

@Component(service = DefaultHttpClientFactory.class)
@Designate(ocd = HttpClientProviderConfig.class)
public class DefaultHttpClientFactory {

  @Reference
  private HttpClientBuilderFactory httpClientBuilderFactory;

  private HttpClientProviderConfig config;

  @Activate
  @Modified
  private void activate(HttpClientProviderConfig config) {
    this.config = config;
  }

  public CloseableHttpClient createNewClient() {
    PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager();
    connMgr.setMaxTotal(config.max_total());
    connMgr.setDefaultMaxPerRoute(config.max_per_route());
    return httpClientBuilderFactory.newBuilder()
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

}
