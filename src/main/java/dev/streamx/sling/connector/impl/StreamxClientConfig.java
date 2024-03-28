package dev.streamx.sling.connector.impl;

public interface StreamxClientConfig {

  String getStreamxUrl();

  String getAuthToken();

  String[] getResourcePathPatterns();

}
