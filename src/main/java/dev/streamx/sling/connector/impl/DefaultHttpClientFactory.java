package dev.streamx.sling.connector.impl;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.apache.http.ssl.SSLContextBuilder;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default HTTP client factory.
 */
@Component(
    service = DefaultHttpClientFactory.class,
    immediate = true
)
@Designate(ocd = HttpClientProviderConfig.class)
@ServiceDescription("Default HTTP client factory")
public class DefaultHttpClientFactory {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpClientFactory.class);

  private final HttpClientBuilderFactory httpClientBuilderFactory;
  private final AtomicReference<HttpClientProviderConfig> config;

  /**
   * Constructs an instance of this class.
   * @param httpClientBuilderFactory underlying factory for creating HTTP clients
   * @param config configuration for this service
   */
  @Activate
  public DefaultHttpClientFactory(
      @Reference(cardinality = ReferenceCardinality.MANDATORY)
      HttpClientBuilderFactory httpClientBuilderFactory,
      HttpClientProviderConfig config
  ) {
    this.httpClientBuilderFactory = httpClientBuilderFactory;
    this.config = new AtomicReference<>(config);
  }

  @Modified
  private void configure(HttpClientProviderConfig config) {
    this.config.set(config);
  }

  private Optional<LayeredConnectionSocketFactory> withTrustAllTlsCertificates() {
    try {
      LOG.trace("Building {}", LayeredConnectionSocketFactory.class.getName());
      TrustStrategy trustStrategy = (chain, authType) -> true;
      SSLContext sslContext = SSLContextBuilder.create()
          .loadTrustMaterial(null, trustStrategy)
          .build();
      return Optional.of(new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE));
    } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException exception) {
      String message = String.format(
          "Could not create %s", LayeredConnectionSocketFactory.class.getName()
      );
      LOG.error(message, exception);
      return Optional.empty();
    }
  }

  private PoolingHttpClientConnectionManager connectionManager() {
    boolean doTrustAllTlsCerts = config.get().insecure();
    PoolingHttpClientConnectionManager connMgr;
    if (doTrustAllTlsCerts) {
      connMgr = withTrustAllTlsCertificates()
          .map(
              sslSocketFactory -> {
                PlainConnectionSocketFactory plainSocketFactory
                    = PlainConnectionSocketFactory.getSocketFactory();
                return RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", plainSocketFactory)
                    .register("https", sslSocketFactory)
                    .build();
              }
          ).map(
              registry -> {
                LOG.debug("Using custom connection manager");
                return new PoolingHttpClientConnectionManager(registry);
              }
          ).orElseThrow();
    } else {
      connMgr = new PoolingHttpClientConnectionManager();
    }
    connMgr.setMaxTotal(config.get().max_total());
    connMgr.setDefaultMaxPerRoute(config.get().max_per_route());
    return connMgr;
  }

  CloseableHttpClient createNewClient() {
    HttpClientProviderConfig unwrappedConfig = config.get();
    return httpClientBuilderFactory.newBuilder()
        .setConnectionManager(connectionManager())
        .setKeepAliveStrategy(
            (response, context) -> unwrappedConfig.keep_alive_time()
        )
        .evictIdleConnections(
            unwrappedConfig.idle_connection_keep_alive_time(), TimeUnit.MILLISECONDS
        ).evictExpiredConnections()
        .setDefaultRequestConfig(RequestConfig.custom()
            .setConnectTimeout(unwrappedConfig.connection_timeout())
            .setSocketTimeout(unwrappedConfig.socket_timeout())
            .setConnectionRequestTimeout(unwrappedConfig.connection_request_timeout())
            .build())
        .build();
  }
}
