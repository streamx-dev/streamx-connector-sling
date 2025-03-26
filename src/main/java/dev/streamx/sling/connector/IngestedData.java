package dev.streamx.sling.connector;

import java.util.Map;
import org.apache.sling.api.uri.SlingUri;

public interface IngestedData {

  PublicationAction ingestionAction();

  SlingUri uriToIngest();

  Map<String, Object> properties();
}
