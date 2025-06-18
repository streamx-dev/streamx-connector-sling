package dev.streamx.sling.connector.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.testing.handlers.AssetPublicationHandler;
import dev.streamx.sling.connector.testing.handlers.PagePublicationHandler;
import java.util.List;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

@ExtendWith(SlingContextExtension.class)
class PublicationHandlerRegistryTest {

  private final SlingContext slingContext = new SlingContext(ResourceResolverType.NONE);
  private final ResourceResolver resourceResolver = mock(ResourceResolver.class);

  @Test
  void shouldBindAndUnbindHandlers() {
    // given
    PagePublicationHandler pagePublicationHandler = new PagePublicationHandler(resourceResolver);
    AssetPublicationHandler assetPublicationHandler = new AssetPublicationHandler(resourceResolver);

    BundleContext bundleContext = slingContext.bundleContext();

    // when: bind handlers
    List<ServiceRegistration<?>> registeredHandlers = List.of(
        bundleContext.registerService(PublicationHandler.class, pagePublicationHandler, null),
        bundleContext.registerService(PublicationHandler.class, assetPublicationHandler, null)
    );

    PublicationHandlerRegistry registry = new PublicationHandlerRegistry();
    slingContext.registerInjectActivateService(registry);

    // then
    assertThat(registry.getHandlers()).containsExactlyInAnyOrder(
        pagePublicationHandler,
        assetPublicationHandler
    );

    // when: unbind handlers
    registeredHandlers.forEach(ServiceRegistration::unregister);

    // then
    assertThat(registry.getHandlers()).isEmpty();
  }

}