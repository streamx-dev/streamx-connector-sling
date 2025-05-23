package dev.streamx.sling.connector.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.osgi.services.HttpClientBuilderFactory;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DefaultHttpClientFactoryTest {

  @SuppressWarnings({
      "StaticVariableMayNotBeInitialized", "NonConstantFieldWithUpperCaseName", "squid:S3008"
  })
  private static Server SERVER;
  @SuppressWarnings({
      "StaticVariableMayNotBeInitialized", "NonConstantFieldWithUpperCaseName", "squid:S3008"
  })
  private static int HTTP_PORT;
  @SuppressWarnings({
      "StaticVariableMayNotBeInitialized", "NonConstantFieldWithUpperCaseName", "squid:S3008"
  })
  private static int HTTPS_PORT;

  @BeforeAll
  static void startJetty() throws Exception {
    SERVER = new Server();

    HttpConfiguration httpConfig = new HttpConfiguration();
    ServerConnector httpConnector = new ServerConnector(
        SERVER,
        new HttpConnectionFactory(httpConfig)
    );
    httpConnector.setPort(0); // ephemeral
    SERVER.addConnector(httpConnector);

    KeyStore keyStore = build();
    SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
    sslContextFactory.setKeyStore(keyStore);
    sslContextFactory.setKeyStorePassword("password");
    HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
    httpsConfig.addCustomizer(new SecureRequestCustomizer());
    ServerConnector httpsConnector = new ServerConnector(
        SERVER,
        new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
        new HttpConnectionFactory(httpsConfig)
    );
    httpsConnector.setPort(0); // ephemeral
    SERVER.addConnector(httpsConnector);

    ServletContextHandler servletContextHandler = servletContextHandler();
    SERVER.setHandler(servletContextHandler);
    SERVER.start();
    HTTP_PORT = httpConnector.getLocalPort();
    HTTPS_PORT = httpsConnector.getLocalPort();
  }

  private static ServletContextHandler servletContextHandler() {
    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");
    ServletHolder servletHolder = new ServletHolder("hello-servlet", new HttpServlet() {
      @Override
      protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("text/plain");
        resp.getWriter().println("Hello from Jetty");
      }
    });
    context.addServlet(servletHolder, "/*");
    return context;
  }

  @SuppressWarnings("StaticVariableUsedBeforeInitialization")
  @AfterAll
  static void stopJetty() throws Exception {
    SERVER.stop();
  }

  @SuppressWarnings("MagicNumber")
  private static KeyStore build() throws Exception {
    // Ensure the Bouncy Castle provider is registered
    Security.addProvider(new BouncyCastleProvider());

    // 1) Generate a 2048-bit RSA key pair
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair keyPair = kpg.generateKeyPair();

    // 2) Build an X.509 certificate valid for e.g. 1 year
    long now = System.currentTimeMillis();
    X509v3CertificateBuilder certBuilder = x509v3CertificateBuilder(now, keyPair);

    GeneralName localhostSAN = new GeneralName(GeneralName.dNSName, "localhost");
    ASN1Encodable subjectAltNames = new GeneralNames(localhostSAN);
    certBuilder.addExtension(
        Extension.subjectAlternativeName,
        false, // not critical
        subjectAltNames
    );

    ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
        .build(keyPair.getPrivate());

    X509Certificate cert = new JcaX509CertificateConverter()
        .setProvider("BC")
        .getCertificate(certBuilder.build(signer));

    // Sanity-check the self-signed cert
    cert.checkValidity(new Date());
    cert.verify(keyPair.getPublic());

    // 3) Create an empty JKS KeyStore, insert our private key + self-signed cert
    KeyStore keyStore = KeyStore.getInstance("JKS");
    keyStore.load(null, null);  // initialize empty
    keyStore.setKeyEntry(
        "selfsigned",
        keyPair.getPrivate(),
        "password".toCharArray(),
        new Certificate[]{cert}
    );

    return keyStore;
  }

  private static X509v3CertificateBuilder x509v3CertificateBuilder(long now,
      KeyPair keyPair) {
    Date startDate = new Date(now - TimeUnit.HOURS.toMillis(1)); // backdate 1 hour
    @SuppressWarnings("MagicNumber") Date endDate = new Date(now + TimeUnit.DAYS.toMillis(365)); // 1 year validity

    X500Name subject = new X500Name("CN=Test"); // issuer = subject for self-signed
    BigInteger serial = BigInteger.valueOf(now);

    return new JcaX509v3CertificateBuilder(
        subject,
        serial,
        startDate,
        endDate,
        subject,  // subject
        keyPair.getPublic()
    );
  }

  @Test
  @SuppressWarnings({"OverlyBroadThrowsClause", "NestedTryStatement"})
  void testHttpConnection() throws IOException {
    SlingContext context = new SlingContext();
    context.registerService(HttpClientBuilderFactory.class, HttpClients::custom);
    DefaultHttpClientFactory factory = context.registerInjectActivateService(
        DefaultHttpClientFactory.class
    );
    try (CloseableHttpClient client = factory.createNewClient()) {
      HttpUriRequest requestToHTTP = new HttpGet("http://localhost:" + HTTP_PORT);
      try (CloseableHttpResponse response = client.execute(requestToHTTP)) {
        verifyResponse(response, HttpServletResponse.SC_OK, "Hello from Jetty");
      }
      HttpUriRequest requestToHTTPS = new HttpGet("https://localhost:" + HTTPS_PORT);
      assertThatThrownBy(() -> client.execute(requestToHTTPS)).isInstanceOf(SSLException.class);
    }
  }

  @Test
  @SuppressWarnings({"OverlyBroadThrowsClause", "NestedTryStatement"})
  void testHttpsConnectionWithTrustAll() throws IOException {
    SlingContext context = new SlingContext();
    context.registerService(HttpClientBuilderFactory.class, HttpClients::custom);
    DefaultHttpClientFactory factory = context.registerInjectActivateService(
        DefaultHttpClientFactory.class, Map.of("insecure", true)
    );

    try (CloseableHttpClient client = factory.createNewClient()) {
      HttpUriRequest requestToHTTP = new HttpGet("http://localhost:" + HTTP_PORT);
      try (CloseableHttpResponse response = client.execute(requestToHTTP)) {
        verifyResponse(response, HttpServletResponse.SC_OK, "Hello from Jetty");
      }
      HttpUriRequest requestToHTTPS = new HttpGet("https://localhost:" + HTTPS_PORT);
      try (CloseableHttpResponse response = client.execute(requestToHTTPS)) {
        verifyResponse(response, HttpServletResponse.SC_OK, "Hello from Jetty");
      }
    }
  }

  private static void verifyResponse(HttpResponse response, int expectedStatus, String expectedContent)
      throws IOException {
    assertThat(response.getStatusLine().getStatusCode()).isEqualTo(expectedStatus);
    assertThat(response.getEntity().getContent()).hasContent(expectedContent);
  }
}
