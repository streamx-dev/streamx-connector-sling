package dev.streamx.sling.connector.impl;

import org.osgi.service.component.annotations.Component;

@Component(service = StreamxClientConfig.class)
class FakeStreamxClientConfig implements StreamxClientConfig {

  private final String streamxUrl;
  private final String[] resourcePathPatterns;

  FakeStreamxClientConfig(String streamxUrl, String[] resourcePathPatterns) {
    this.streamxUrl = streamxUrl;
    this.resourcePathPatterns = resourcePathPatterns;
  }

  @Override
  public String getStreamxUrl() {
    return streamxUrl;
  }

  @Override
  public String getAuthToken() {
    return null;
  }

  @Override
  public String[] getResourcePathPatterns() {
    return resourcePathPatterns;
  }
}
