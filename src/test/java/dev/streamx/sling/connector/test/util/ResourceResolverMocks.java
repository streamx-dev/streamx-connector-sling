package dev.streamx.sling.connector.test.util;

import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;

public final class ResourceResolverMocks {

  private ResourceResolverMocks() {
    // no instances
  }

  @SuppressWarnings("deprecation")
  public static void configure(
      ResourceResolver resourceResolverMock,
      ResourceResolverFactory resourceResolverFactoryMock) {

    try {
      doReturn(resourceResolverMock).when(resourceResolverFactoryMock).getAdministrativeResourceResolver(null);
      doNothing().when(resourceResolverMock).close();
    } catch (LoginException ex) {
      fail(ex.getMessage(), ex);
    }
  }
}