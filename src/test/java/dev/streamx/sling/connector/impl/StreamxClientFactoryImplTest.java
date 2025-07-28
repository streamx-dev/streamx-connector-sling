package dev.streamx.sling.connector.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.streamx.sling.connector.ResourceInfo;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class StreamxClientFactoryImplTest {

  @Test
  void shouldCreateStreamxClientFactory() throws Exception {
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
    StreamxClientFactoryImpl streamxClientFactory = new StreamxClientFactoryImpl();
    Field defaultHttpClientFactoryField = streamxClientFactory.getClass().getDeclaredField("defaultHttpClientFactory");
    defaultHttpClientFactoryField.setAccessible(true);
    defaultHttpClientFactoryField.set(streamxClientFactory, mock(DefaultHttpClientFactory.class));

    StreamxInstanceClient streamxClient = streamxClientFactory
        .createStreamxClient(new StreamxClientConfigImpl(config));

    // then
    assertThat(streamxClient.getName()).isEqualTo(config.name());
    assertThat(streamxClient.canProcess(new ResourceInfo("any-path"))).isTrue();
  }
}