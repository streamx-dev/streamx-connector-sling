package dev.streamx.sling.connector.impl;

import java.util.Arrays;
import java.util.List;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

@Component(service = StreamxClientConfig.class)
@Designate(ocd = StreamxClientConfigOcd.class, factory = true)
public class StreamxClientConfigImpl implements StreamxClientConfig {

  private String name;
  private String streamxUrl;
  private String authToken;
  private List<String> resourcePathPatterns;

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getStreamxUrl() {
    return streamxUrl;
  }

  @Override
  public String getAuthToken() {
    return authToken;
  }

  @Override
  public List<String> getResourcePathPatterns() {
    return resourcePathPatterns;
  }

  @Activate
  @Modified
  private void activate(StreamxClientConfigOcd config) {
    this.name = config.name();
    this.streamxUrl = config.streamxUrl();
    this.authToken = config.authToken();
    this.resourcePathPatterns = Arrays.asList(config.resourcePathPatterns());
  }
}
