package dev.streamx.sling.connector.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link StreamxClientConfig}.
 */
@Component(
    service = StreamxClientConfig.class,
    immediate = true
)
@Designate(
    ocd = StreamxClientConfigOcd.class,
    factory = true
)
@ServiceDescription(StreamxClientConfigImpl.SERVICE_DESCRIPTION)
public class StreamxClientConfigImpl implements StreamxClientConfig {

  static final String SERVICE_DESCRIPTION = "Configuration of the client of StreamX "
      + "REST Ingestion Service";
  private static final Logger LOG = LoggerFactory.getLogger(StreamxClientConfigImpl.class);
  private final AtomicReference<String> name = new AtomicReference<>();
  private final AtomicReference<String> streamxUrl = new AtomicReference<>();
  private final AtomicReference<OSGiSecret> authToken = new AtomicReference<>();
  private final AtomicReference<List<String>> resourcePathPatterns = new AtomicReference<>();

  /**
   * Constructs an instance of this class.
   * @param config configuration for this service
   */
  @Activate
  public StreamxClientConfigImpl(StreamxClientConfigOcd config) {
    configure(config);
  }

  @Modified
  private void configure(StreamxClientConfigOcd config) {
    name.set(config.name());
    streamxUrl.set(config.streamxUrl());
    authToken.set(new OSGiSecret(config.authToken()));
    resourcePathPatterns.set(Arrays.asList(config.resourcePathPatterns()));
    LOG.trace(
        "Applied configuration. Name: '{}'. URL: '{}'. Resource path patterns: '{}'.",
        name, streamxUrl, resourcePathPatterns
    );
  }

  @Override
  public String getName() {
    return name.get();
  }

  @Override
  public String getStreamxUrl() {
    return streamxUrl.get();
  }

  @Override
  public String getAuthToken() {
    return authToken.get().get();
  }

  @Override
  public List<String> getResourcePathPatterns() {
    return Collections.unmodifiableList(resourcePathPatterns.get());
  }
}
