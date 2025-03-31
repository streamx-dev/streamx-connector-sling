package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.PublicationHandler;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for {@link PublicationHandler}s.
 */
@Component(service = PublicationHandlerRegistry.class)
public class PublicationHandlerRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(PublicationHandlerRegistry.class);

  private final List<PublicationHandler<?>> handlers;

  /**
   * Constructs an instance of this class.
   */
  @Activate
  public PublicationHandlerRegistry() {
    handlers = new CopyOnWriteArrayList<>();
  }

  @Reference(
      service = PublicationHandler.class,
      cardinality = ReferenceCardinality.AT_LEAST_ONE,
      policy = ReferencePolicy.DYNAMIC)
  private void bindOperation(PublicationHandler<?> handler) {
    handlers.add(handler);
    LOG.info("Added: {}, handlers count: {}", handler.getClass().getName(), handlers.size());
  }

  private void unbindOperation(PublicationHandler<?> handler) {
    handlers.remove(handler);
    LOG.info("Removed: {}, handlers count: {}", handler.getClass().getName(), handlers.size());
  }

  List<PublicationHandler<?>> getHandlers() {
    return Collections.unmodifiableList(handlers);
  }

}
