package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.RelatedResourcesSelector;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for {@link RelatedResourcesSelector}s.
 */
@Component(service = RelatedResourcesSelectorRegistry.class)
public class RelatedResourcesSelectorRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(RelatedResourcesSelectorRegistry.class);

  private final List<RelatedResourcesSelector> selectors = new CopyOnWriteArrayList<>();

  /**
   * Constructs an instance of this class.
   */
  RelatedResourcesSelectorRegistry() {
  }

  @Reference(
      service = RelatedResourcesSelector.class,
      cardinality = ReferenceCardinality.MULTIPLE,
      policy = ReferencePolicy.DYNAMIC)
  private void bindOperation(RelatedResourcesSelector selector) {
    selectors.add(selector);
    LOG.info("Added: {}, selectors count: {}", selector.getClass().getName(), selectors.size());
  }

  private void unbindOperation(RelatedResourcesSelector selector) {
    selectors.remove(selector);
    LOG.info("Removed: {}, selectors count: {}", selector.getClass().getName(), selectors.size());
  }

  List<RelatedResourcesSelector> getSelectors() {
    return new ArrayList<>(selectors);
  }

}
