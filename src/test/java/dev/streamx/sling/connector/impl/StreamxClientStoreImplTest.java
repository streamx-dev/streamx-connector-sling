package dev.streamx.sling.connector.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import dev.streamx.clients.ingestion.StreamxClient;
import java.lang.annotation.Annotation;
import java.util.List;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

@ExtendWith(SlingContextExtension.class)
class StreamxClientStoreImplTest {

  private final SlingContext slingContext = new SlingContext(ResourceResolverType.NONE);

  @Test
  void shouldBindAndUnbindConfigs() throws Exception {
    // given
    StreamxClientConfig config1 = new StreamxClientConfigImpl(
        new StreamxClientConfigOcdImpl("client-1", "http://streamx-1.dev")
    );
    StreamxClientConfig config2 = new StreamxClientConfigImpl(
        new StreamxClientConfigOcdImpl("client-2", "http://streamx-2.dev")
    );

    StreamxClientFactory clientFactory = mock(StreamxClientFactory.class);
    StreamxClient clientMock = mock(StreamxClient.class);
    doReturn(new StreamxInstanceClient(clientMock, config1)).when(clientFactory).createStreamxClient(config1);
    doReturn(new StreamxInstanceClient(clientMock, config2)).when(clientFactory).createStreamxClient(config2);

    BundleContext bundleContext = slingContext.bundleContext();

    // when: bind configs
    List<ServiceRegistration<?>> registeredConfigs = List.of(
        bundleContext.registerService(StreamxClientConfig.class, config1, null),
        bundleContext.registerService(StreamxClientConfig.class, config2, null)
    );

    StreamxClientStoreImpl store = new StreamxClientStoreImpl(clientFactory);
    slingContext.registerInjectActivateService(store);

    // then
    assertThat(store.getByName("client-1"))
        .isNotNull()
        .extracting(StreamxInstanceClient::getName).isEqualTo("client-1");
    assertThat(store.getByName("client-2"))
        .isNotNull()
        .extracting(StreamxInstanceClient::getName).isEqualTo("client-2");

    // when: unbind configs
    registeredConfigs.forEach(ServiceRegistration::unregister);

    // then
    assertThat(store.getByName("client-1")).isNull();
    assertThat(store.getByName("client-2")).isNull();
  }

  private static class StreamxClientConfigOcdImpl implements StreamxClientConfigOcd {

    private final String name;
    private final String streamxUrl;

    private StreamxClientConfigOcdImpl(String name, String streamxUrl) {
      this.name = name;
      this.streamxUrl = streamxUrl;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return StreamxClientConfigOcd.class;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public String streamxUrl() {
      return streamxUrl;
    }

    @Override
    public String authToken() {
      return "secret";
    }

    @Override
    public String[] resourcePathPatterns() {
      return new String[] {
          ".*"
      };
    }
  }

}