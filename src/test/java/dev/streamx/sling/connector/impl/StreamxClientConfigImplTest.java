package dev.streamx.sling.connector.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.junit.jupiter.api.Test;

class StreamxClientConfigImplTest {

  @Test
  void mustReturnConfiguredValues() {
    SlingContext context = new SlingContext();
    String name = "streamx-client-test-name";
    String streamxUrl = "http://localhost:9999";
    String authToken = "secret-token";
    String[] resourcePathPatterns = {"/content/.*", "/apps/.*"};
    StreamxClientConfig streamxClientConfig = context.registerInjectActivateService(
        StreamxClientConfigImpl.class, Map.of(
            "name", name,
            "streamxUrl", streamxUrl,
            "authToken", authToken,
            "resourcePathPatterns", resourcePathPatterns
        ));

    assertThat(streamxClientConfig.getName()).isEqualTo(name);
    assertThat(streamxClientConfig.getStreamxUrl()).isEqualTo(streamxUrl);
    assertThat(streamxClientConfig.getAuthToken()).isEqualTo(authToken);
    assertThat(streamxClientConfig.getResourcePathPatterns()).containsExactly(resourcePathPatterns);
  }

  @Test
  void mustFallbackForFailedInterpolation() {
    SlingContext context = new SlingContext();
    String authToken = "$[secret:STREAMX_CLIENT_AUTH_TOKEN]";
    StreamxClientConfig streamxClientConfig = context.registerInjectActivateService(
        StreamxClientConfigImpl.class, Map.of("authToken", authToken));
    assertThat(streamxClientConfig.getAuthToken()).isEmpty();
  }
}
