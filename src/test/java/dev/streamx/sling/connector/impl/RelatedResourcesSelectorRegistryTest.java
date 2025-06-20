package dev.streamx.sling.connector.impl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.streamx.sling.connector.RelatedResourcesSelector;
import java.util.Collection;
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
    RelatedResourcesSelector selector1 = new RelatedResourcesSelector() {
      @Override
      public Collection<String> getRelatedResources(String resourcePath) {
        return List.of(
            "/content/pages/page.html",
            "/content/dam/image.jpg"
        );
      }

      @Override
      public void removeParentResources(Collection<String> relatedResourcePaths, Collection<String> parentResourcePaths) {
      }
    };

    RelatedResourcesSelector selector2 = new RelatedResourcesSelector() {
      @Override
      public Collection<String> getRelatedResources(String resourcePath) {
        return List.of(
            "/content/pages/other-page.html",
            "/content/dam/other-image.jpg"
        );
      }

      @Override
      public void removeParentResources(Collection<String> relatedResourcePaths, Collection<String> parentResourcePaths) {
      }
    };

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