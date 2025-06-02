package dev.streamx.sling.connector.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import dev.streamx.clients.ingestion.exceptions.StreamxClientException;
import java.lang.annotation.Annotation;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;

class StreamxClientFactoryImplTest {

  @Test
  void shouldCreateStreamxClientFactory() throws StreamxClientException {
    // given
    StreamxClientConfigOcd config = new StreamxClientConfigOcd() {

      @Override
      public Class<? extends Annotation> annotationType() {
        return StreamxClientConfigOcd.class;
      }

      @Override
      public String name() {
        return "test-client";
      }

      @Override
      public String streamxUrl() {
        return "http://not-existing-streamx-url";
      }

      @Override
      public String authToken() {
        return "secret";
      }

      @Override
      public String[] resourcePathPatterns() {
        return new String[]{
            ".*"
        };
      }
    };

    // when
    StreamxClientFactoryImpl streamxClientFactory = spy(new StreamxClientFactoryImpl());
    doReturn(HttpClients.createDefault()).when(streamxClientFactory).createNewClient();

    StreamxInstanceClient streamxClient = streamxClientFactory
        .createStreamxClient(new StreamxClientConfigImpl(config));

    // then
    assertThat(streamxClient.getName()).isEqualTo(config.name());
    assertThat(streamxClient.canProcess("any-path")).isTrue();
  }
}