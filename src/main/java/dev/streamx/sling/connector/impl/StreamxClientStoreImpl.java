package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.exceptions.StreamxClientException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = StreamxClientStore.class)
public class StreamxClientStoreImpl implements StreamxClientStore {

  private static final Logger LOG = LoggerFactory.getLogger(StreamxClientStoreImpl.class);
  private final Map<String, StreamxInstanceClient> streamxInstanceClients = new ConcurrentHashMap<>();

  @Reference
  private StreamxClientFactory streamxClientFactory;

  @Reference(
      service = StreamxClientConfig.class,
      cardinality = ReferenceCardinality.AT_LEAST_ONE,
      policyOption = ReferencePolicyOption.GREEDY
  )
  private List<StreamxClientConfig> configs;

  public synchronized void unbind(StreamxClientConfig config) {
    configs.remove(config);
    streamxInstanceClients.remove(config.getStreamxUrl());
  }

  @Activate
  @Modified
  private void activate() {
    configs.forEach(config -> streamxInstanceClients.computeIfAbsent(config.getStreamxUrl(),
            key -> initStreamxInstanceClient(config)));
  }

  @Override
  public List<StreamxInstanceClient> getForResource(String resourcePath) {
    return streamxInstanceClients.values().stream()
        .filter(Objects::nonNull)
        .filter(client -> client.canProcess(resourcePath))
        .collect(Collectors.toList());
  }

  private StreamxInstanceClient initStreamxInstanceClient(StreamxClientConfig config) {
    try {
      return streamxClientFactory.createStreamxClient(config);
    } catch (StreamxClientException e) {
      LOG.error("An error occurred during the creation of the StreamX client.", e);
      return null;
    }
  }
}
