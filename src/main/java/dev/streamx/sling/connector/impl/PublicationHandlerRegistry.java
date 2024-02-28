package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.PublicationHandler;
import java.util.ArrayList;
import java.util.List;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

@Component(service = PublicationHandlerRegistry.class)
public class PublicationHandlerRegistry {

  private final List<PublicationHandler<?>> publicationHandlers = new ArrayList<>();

  @Reference(
      service = PublicationHandler.class,
      cardinality = ReferenceCardinality.AT_LEAST_ONE,
      policy = ReferencePolicy.DYNAMIC)
  private void bindOperation(PublicationHandler<?> handler) {
    publicationHandlers.add(handler);
  }

  private void unbindOperation(PublicationHandler<?> handler) {
    publicationHandlers.remove(handler);
  }

  List<PublicationHandler<?>> getHandlers() {
    return publicationHandlers;
  }

}
