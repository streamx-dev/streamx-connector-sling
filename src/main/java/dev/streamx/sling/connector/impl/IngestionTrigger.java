package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.PublicationAction;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.sling.event.jobs.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Trigger to ingest data into Streamx.
 */
class IngestionTrigger {

  static final String JOB_TOPIC = "dev/streamx/ingestion-trigger";
  static final String PN_STREAMX_INGESTION_ACTION = "streamx.ingestionAction";
  static final String PN_STREAMX_RESOURCES_INFO = "streamx.resourcesInfo";

  private static final Logger LOG = LoggerFactory.getLogger(IngestionTrigger.class);
  private final PublicationAction ingestionAction;
  private final List<ResourceInfo> resourcesInfo;

  /**
   * Constructs an instance of this class.
   *
   * @param job                     {@link Job} that should be converted into the newly created
   *                                {@link IngestionTrigger}
   */
  IngestionTrigger(Job job) {
    LOG.trace("Decomposing {}", job);

    String ingestionActionRaw = job.getProperty(PN_STREAMX_INGESTION_ACTION, String.class);
    this.ingestionAction = PublicationAction.of(ingestionActionRaw).orElseThrow();

    String[] resourcesInfoRaw = job.getProperty(PN_STREAMX_RESOURCES_INFO, String[].class);
    this.resourcesInfo = Stream.of(resourcesInfoRaw)
        .map(ResourceInfo::deserialize)
        .filter(resource -> resource.getPath() != null)
        .collect(Collectors.toUnmodifiableList());

    LOG.trace("Decomposed {} into [{} {}]", job, ingestionAction, resourcesInfo);
  }

  /**
   * Constructs an instance of this class.
   *
   * @param ingestionAction {@link PublicationAction} to be performed as the result of this
   *                        {@link IngestionTrigger} activation
   * @param resourcesInfo   {@link List} of {@link ResourceInfo} objects to be ingested as the result of this
   *                        {@link IngestionTrigger} activation
   */
  IngestionTrigger(PublicationAction ingestionAction, List<ResourceInfo> resourcesInfo) {
    this.ingestionAction = ingestionAction;
    this.resourcesInfo = Collections.unmodifiableList(resourcesInfo);
  }

  /**
   * Returns the {@link PublicationAction} to be performed as the result of this
   * {@link IngestionTrigger} activation.
   *
   * @return {@link PublicationAction} to be performed as the result of this
   * {@link IngestionTrigger}
   */
  PublicationAction ingestionAction() {
    return ingestionAction;
  }

  /**
   * {@link List} of {@link ResourceInfo}s to be ingested as the result of this {@link IngestionTrigger}
   * activation
   *
   * @return {@link List} of {@link ResourceInfo}s to be ingested as the result of this
   * {@link IngestionTrigger} activation
   */
  List<ResourceInfo> resourcesInfo() {
    return resourcesInfo;
  }

  /**
   * Returns properties of this {@link IngestionTrigger} out of which a new {@link Job} representing
   * this {@link IngestionTrigger} can be created.
   *
   * @return properties of this {@link IngestionTrigger} out of which a new {@link Job} representing
   * this {@link IngestionTrigger} can be created
   */
  @SuppressWarnings("unused")
  Map<String, Object> asJobProps() {
    return Map.of(
        PN_STREAMX_INGESTION_ACTION, ingestionAction.toString(),
        PN_STREAMX_RESOURCES_INFO, resourcesInfo.stream().map(ResourceInfo::serialize).toArray(String[]::new)
    );
  }
}
