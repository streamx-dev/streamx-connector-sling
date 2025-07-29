package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.ResourceInfo;
import java.util.Collection;
import java.util.Map;
import org.apache.sling.event.jobs.Job;

class IngestionTriggerJobProperties {

  private static final String PN_STREAMX_INGESTION_ACTION = "streamx.ingestion.action";
  private static final String PN_STREAMX_INGESTION_RESOURCES = "streamx.ingestion.resources";

  private String action;
  private String[] resources;

  IngestionTriggerJobProperties withAction(PublicationAction action) {
    this.action = action.toString();
    return this;
  }

  IngestionTriggerJobProperties withResources(Collection<ResourceInfo> resources) {
    this.resources = resources.stream().map(ResourceInfo::serialize).toArray(String[]::new);
    return this;
  }

  Map<String, Object> asMap() {
    return Map.of(
        PN_STREAMX_INGESTION_ACTION, action,
        PN_STREAMX_INGESTION_RESOURCES, resources
    );
  }

  static String getAction(Job job) {
    return job.getProperty(PN_STREAMX_INGESTION_ACTION, String.class);
  }

  static String[] getResources(Job job) {
    return job.getProperty(PN_STREAMX_INGESTION_RESOURCES, String[].class);
  }
}
