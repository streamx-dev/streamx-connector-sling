package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.IngestionDataFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = PublicationHandlerRegistry.class)
public class PublicationHandlerRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(PublicationHandlerRegistry.class);

  private final List<IngestionDataFactory<?>> handlers = new CopyOnWriteArrayList<>();

  @Reference(
      service = IngestionDataFactory.class,
      cardinality = ReferenceCardinality.AT_LEAST_ONE,
      policy = ReferencePolicy.DYNAMIC)
  private void bindOperation(IngestionDataFactory<?> handler) {
    handlers.add(handler);
    LOG.info("Added: {}, handlers count: {}", handler.getClass().getName(), handlers.size());
  }

  private void unbindOperation(IngestionDataFactory<?> handler) {
    handlers.remove(handler);
    LOG.info("Removed: {}, handlers count: {}", handler.getClass().getName(), handlers.size());
  }

  List<IngestionDataFactory<?>> getHandlers() {
    return new ArrayList<>(handlers);
  }

}
