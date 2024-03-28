package dev.streamx.sling.connector.impl;

import java.util.List;

public interface StreamxClientConfig {

  String getStreamxUrl();

  String getAuthToken();

  List<String> getResourcePathPatterns();

}
