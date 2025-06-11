package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.PublicationAction;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.sling.event.jobs.Job;

/**
 * Trigger to ingest data into Streamx.
 */
final class IngestionTriggerJobHelper {

  static final String JOB_TOPIC = "dev/streamx/ingestion-trigger";
  static final String PN_STREAMX_INGESTION_ACTION = "streamx.ingestionAction";
  static final String PN_STREAMX_RESOURCES_INFO = "streamx.resourcesInfo";

  private IngestionTriggerJobHelper() {
    // no instances
  }

  static PublicationAction extractPublicationAction(Job job) {
    String publicationActionRaw = job.getProperty(PN_STREAMX_INGESTION_ACTION, String.class);
    return PublicationAction.of(publicationActionRaw).orElseThrow();
  }

  static List<ResourceInfo> extractResourcesInfo(Job job) {
    String[] resourcesInfoRaw = job.getProperty(PN_STREAMX_RESOURCES_INFO, String[].class);
    return Stream.of(resourcesInfoRaw)
        .map(ResourceInfo::deserialize)
        .filter(resource -> resource.getPath() != null)
        .collect(Collectors.toUnmodifiableList());
  }

  static Map<String, Object> asJobProps(PublicationAction publicationAction, List<ResourceInfo> resourcesInfo) {
    return Map.of(
        PN_STREAMX_INGESTION_ACTION, publicationAction.toString(),
        PN_STREAMX_RESOURCES_INFO, resourcesInfo.stream().map(ResourceInfo::serialize).toArray(String[]::new)
    );
  }
}
