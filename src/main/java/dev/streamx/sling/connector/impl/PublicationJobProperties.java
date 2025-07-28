package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.ResourceInfo;
import java.util.Map;
import org.apache.sling.event.jobs.Job;

class PublicationJobProperties {

  private static final String PN_STREAMX_PUBLICATION_HANDLER_ID = "streamx.publication.handler.id";
  private static final String PN_STREAMX_PUBLICATION_CLIENT_NAME = "streamx.publication.client.name";
  private static final String PN_STREAMX_PUBLICATION_ACTION = "streamx.publication.action";
  private static final String PN_STREAMX_PUBLICATION_PATH = "streamx.publication.path";
  private static final String PN_STREAMX_PUBLICATION_PROPERTIES = "streamx.publication.properties";

  private String handlerId;
  private String clientName;
  private String action;
  private String resourcePath;
  private String resourceProperties;

  PublicationJobProperties withHandlerId(String handlerId) {
    this.handlerId = handlerId;
    return this;
  }

  PublicationJobProperties withClientName(String clientName) {
    this.clientName = clientName;
    return this;
  }

  PublicationJobProperties withAction(PublicationAction action) {
    this.action = action.toString();
    return this;
  }

  PublicationJobProperties withResource(ResourceInfo resourceInfo) {
    this.resourcePath = resourceInfo.getPath();
    this.resourceProperties = resourceInfo.getSerializedProperties();
    return this;
  }

  Map<String, Object> asMap() {
    return Map.of(
        PN_STREAMX_PUBLICATION_HANDLER_ID, handlerId,
        PN_STREAMX_PUBLICATION_CLIENT_NAME, clientName,
        PN_STREAMX_PUBLICATION_ACTION, action,
        PN_STREAMX_PUBLICATION_PATH, resourcePath,
        PN_STREAMX_PUBLICATION_PROPERTIES, resourceProperties
    );
  }

  static String getHandlerId(Job job) {
    return job.getProperty(PN_STREAMX_PUBLICATION_HANDLER_ID, String.class);
  }

  static String getClientName(Job job) {
    return job.getProperty(PN_STREAMX_PUBLICATION_CLIENT_NAME, String.class);
  }

  static String getAction(Job job) {
    return job.getProperty(PN_STREAMX_PUBLICATION_ACTION, String.class);
  }

  static String getResourcePath(Job job) {
    return job.getProperty(PN_STREAMX_PUBLICATION_PATH, String.class);
  }

  static String getResourceProperties(Job job) {
    return job.getProperty(PN_STREAMX_PUBLICATION_PROPERTIES, String.class);
  }
}
