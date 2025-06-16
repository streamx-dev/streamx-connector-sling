package dev.streamx.sling.connector.handlers.resourcepath;

import dev.streamx.sling.connector.PublicationHandler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.engine.SlingRequestProcessor;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    service = {
        ResourcePathPublicationHandler.class, PublicationHandler.class, ClientlibsPublicationHandler.class
    },
    immediate = true,
    configurationPolicy = ConfigurationPolicy.REQUIRE
)
@Designate(ocd = ClientlibsPublicationHandlerConfig.class)
public class ClientlibsPublicationHandler extends ResourcePathPublicationHandler<WebResource> {

  private static final Logger LOG = LoggerFactory.getLogger(ClientlibsPublicationHandler.class);
  private final AtomicReference<ClientlibsPublicationHandlerConfig> config;

  @Activate
  public ClientlibsPublicationHandler(
      @Reference ResourceResolverFactory resourceResolverFactory,
      @Reference SlingRequestProcessor slingRequestProcessor,
      ClientlibsPublicationHandlerConfig config
  ) {
    super(resourceResolverFactory, slingRequestProcessor);
    this.config = new AtomicReference<>(config);
  }

  @Modified
  void configure(ClientlibsPublicationHandlerConfig config) {
    this.config.set(config);
  }

  @Override
  public ResourcePathPublicationHandlerConfig configuration() {
    return new ResourcePathPublicationHandlerConfig() {
      @Override
      public String resourcePathRegex() {
        return config.get().clientlibs_path_regexp();
      }

      @Override
      public String channel() {
        return config.get().publication_channel();
      }

      @Override
      public boolean isEnabled() {
        return config.get().enabled();
      }
    };
  }

  @Override
  public Class<WebResource> modelClass() {
    return WebResource.class;
  }

  @Override
  public WebResource model(InputStream inputStream) {
    return new WebResource(ByteBuffer.wrap(toByteArray(inputStream)));
  }

  private byte[] toByteArray(InputStream inputStream) {
    try {
      return IOUtils.toByteArray(inputStream);
    } catch (IOException exception) {
      LOG.error("Cannot convert input stream to byte array", exception);
      return new byte[NumberUtils.INTEGER_ZERO];
    }
  }

  @Override
  public String getId() {
    return ClientlibsPublicationHandler.class.getSimpleName();
  }
}
