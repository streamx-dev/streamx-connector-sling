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
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = StreamxClientStore.class)
public class StreamxClientStoreImpl implements StreamxClientStore {

  private static final Logger LOG = LoggerFactory.getLogger(StreamxClientStoreImpl.class);

  private final Map<String, StreamxClientConfig> configs = new ConcurrentHashMap<>();
  private final Map<String, StreamxInstanceClient> streamxInstanceClients = new ConcurrentHashMap<>();

  @Reference
  private StreamxClientFactory streamxClientFactory;

  @Reference(service = StreamxClientConfig.class,
      cardinality = ReferenceCardinality.MULTIPLE,
      policy = ReferencePolicy.DYNAMIC,
      bind = "bind",
      unbind = "unbind")
  public synchronized void bind(StreamxClientConfig config) {
    configs.put(config.getStreamxUrl(), config);
  }

  public synchronized void unbind(StreamxClientConfig config) {
    configs.remove(config.getStreamxUrl());
    streamxInstanceClients.remove(config.getStreamxUrl());
  }

  @Activate
  @Modified
  private void activate() {
    configs.keySet()
        .forEach(streamxUrl -> streamxInstanceClients.computeIfAbsent(streamxUrl,
            key -> initStreamxInstanceClient(configs.get(key))));
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
