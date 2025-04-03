package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.StreamxClient;
import dev.streamx.clients.ingestion.exceptions.StreamxClientException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link StreamxClientStore}.
 */
@Component(
    service = StreamxClientStore.class,
    immediate = true,
    reference = @Reference(
        service = StreamxClientConfig.class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY,
        bind = "bindStreamxClientStore",
        unbind = "unbindStreamxClientStore",
        updated = "updateStreamxClientStore",
        policy = ReferencePolicy.DYNAMIC,
        name = "streamxClientConfig"
    )
)
public class StreamxClientStoreImpl implements StreamxClientStore {

  private static final Logger LOG = LoggerFactory.getLogger(StreamxClientStoreImpl.class);

  private final Map<String, StreamxInstanceClient> clientsByName;
  private final StreamxClientFactory streamxClientFactory;

  /**
   * Constructs an instance of this class.
   *
   * @param streamxClientFactory {@link StreamxClientFactory} for creating stored
   *                             {@link StreamxClient} instances
   */
  @Activate
  public StreamxClientStoreImpl(
      @Reference(cardinality = ReferenceCardinality.MANDATORY)
      StreamxClientFactory streamxClientFactory
  ) {
    this.clientsByName = new ConcurrentHashMap<>();
    this.streamxClientFactory = streamxClientFactory;
  }

  @SuppressWarnings("unused")
  void bindStreamxClientStore(StreamxClientConfig config) {
    String configName = config.getName();
    LOG.debug("Adding StreamX client for: '{}'", configName);
    initStreamxInstanceClient(config).ifPresentOrElse(
        client -> clientsByName.put(configName, client),
        () -> LOG.error("An error occurred during adding of the StreamX client: '{}'", configName)
    );
  }

  @SuppressWarnings("unused")
  void unbindStreamxClientStore(StreamxClientConfig config) {
    String configName = config.getName();
    LOG.debug("Removing StreamX client for: '{}'", configName);
    clientsByName.remove(configName);
  }

  @SuppressWarnings("unused")
  void updateStreamxClientStore(StreamxClientConfig config) {
    String configName = config.getName();
    LOG.debug("Updating StreamX client for: '{}'", configName);
    initStreamxInstanceClient(config).ifPresentOrElse(
        client -> clientsByName.put(configName, client),
        () -> LOG.error(
            "An error occurred during the update of the StreamX client: '{}'", configName
        )
    );
  }

  @Override
  public List<StreamxInstanceClient> getForResource(String resourcePath) {
    return clientsByName.values().stream()
        .filter(Objects::nonNull)
        .filter(client -> client.canProcess(resourcePath))
        .collect(Collectors.toList());
  }

  @Override
  public StreamxInstanceClient getByName(String name) {
    return clientsByName.get(name);
  }

  private Optional<StreamxInstanceClient> initStreamxInstanceClient(StreamxClientConfig config) {
    LOG.debug(
        "Initializing StreamX client for: '{}'. URL: '{}'", config.getName(), config.getStreamxUrl()
    );
    try {
      return Optional.of(streamxClientFactory.createStreamxClient(config));
    } catch (StreamxClientException e) {
      LOG.error("An error occurred during the creation of the StreamX client.", e);
      return Optional.empty();
    }
  }
}
