package dev.streamx.sling.connector.test.util;

import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;

public final class ResourceResolverMocks {

  private ResourceResolverMocks() {
    // no instances
  }

  @SuppressWarnings("deprecation")
  public static void configure(SlingContext slingContext, ResourceResolverFactory resourceResolverFactoryMock) {
    try {
      ResourceResolver resourceResolver = spy(slingContext.resourceResolver());
      doNothing().when(resourceResolver).close();
      doReturn(resourceResolver).when(resourceResolverFactoryMock).getAdministrativeResourceResolver(null);
      slingContext.registerService(ResourceResolverFactory.class, resourceResolverFactoryMock);
    } catch (LoginException ex) {
      fail(ex.getMessage(), ex);
    }
  }
}