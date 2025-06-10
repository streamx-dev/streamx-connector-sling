package dev.streamx.sling.connector.impl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.ResourceInfo;
import java.util.List;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

@ExtendWith(SlingContextExtension.class)
class RelatedResourcesSelectorRegistryTest {

  private final SlingContext slingContext = new SlingContext(ResourceResolverType.NONE);

  @Test
  void shouldBindAndUnbindSelectors() {
    // given
    RelatedResourcesSelector selector1 = resourcePath -> List.of(
        new ResourceInfo("/content/pages/page.html", "cq:Page"),
        new ResourceInfo("/content/dam/image.jpg", "dam:Asset")
    );
    RelatedResourcesSelector selector2 = resourcePath -> List.of(
        new ResourceInfo("/content/pages/other-page.html", "cq:Page"),
        new ResourceInfo("/content/dam/other-image.jpg", "dam:Asset")
    );

    BundleContext bundleContext = slingContext.bundleContext();

    // when: bind selectors
    List<ServiceRegistration<?>> registeredSelectors = List.of(
        bundleContext.registerService(RelatedResourcesSelector.class, selector1, null),
        bundleContext.registerService(RelatedResourcesSelector.class, selector2, null)
    );

    RelatedResourcesSelectorRegistry registry = new RelatedResourcesSelectorRegistry();
    slingContext.registerInjectActivateService(registry);

    // then
    assertThat(registry.getSelectors()).containsExactlyInAnyOrder(
        selector1,
        selector2
    );

    // when: unbind selectors
    registeredSelectors.forEach(ServiceRegistration::unregister);

    // then
    assertThat(registry.getSelectors()).isEmpty();
  }

}