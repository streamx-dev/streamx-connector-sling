package dev.streamx.sling.connector.impl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
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
    assertAll(
        () -> assertEquals(name, streamxClientConfig.getName()),
        () -> assertEquals(streamxUrl, streamxClientConfig.getStreamxUrl()),
        () -> assertEquals(authToken, streamxClientConfig.getAuthToken()),
        () -> assertEquals(
            List.of(resourcePathPatterns), streamxClientConfig.getResourcePathPatterns()
        )
    );
  }
}
