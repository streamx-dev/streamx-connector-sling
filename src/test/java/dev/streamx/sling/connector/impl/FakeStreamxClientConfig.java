package dev.streamx.sling.connector.impl;

import java.util.List;
import org.osgi.service.component.annotations.Component;

@Component(service = StreamxClientConfig.class)
class FakeStreamxClientConfig implements StreamxClientConfig {

  private final String streamxUrl;
  private final List<String> resourcePathPatterns;

  FakeStreamxClientConfig(String streamxUrl, List<String> resourcePathPatterns) {
    this.streamxUrl = streamxUrl;
    this.resourcePathPatterns = resourcePathPatterns;
  }

  @Override
  public String getName() {
    return streamxUrl;
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
  public List<String> getResourcePathPatterns() {
    return resourcePathPatterns;
  }
}
