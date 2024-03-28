package dev.streamx.sling.connector.impl;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

@Component(service = StreamxClientConfig.class)
@Designate(ocd = StreamxClientConfigOcd.class, factory = true)
public class StreamxClientConfigImpl implements StreamxClientConfig {

  private String streamxUrl;
  private String authToke;
  private String[] resourcePathPatterns;

  @Override
  public String getStreamxUrl() {
    return streamxUrl;
  }

  @Override
  public String getAuthToken() {
    return authToke;
  }

  @Override
  public String[] getResourcePathPatterns() {
    return resourcePathPatterns;
  }

  @Activate
  @Modified
  private void activate(StreamxClientConfigOcd config) {
    this.streamxUrl = config.streamxUrl();
    this.authToke = config.authToken();
    this.resourcePathPatterns = config.resourcePathPatterns();
  }
}
