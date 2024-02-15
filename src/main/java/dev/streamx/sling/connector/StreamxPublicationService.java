package dev.streamx.sling.connector;

import java.util.List;

public interface StreamxPublicationService {

  boolean isEnabled();

  void publish(List<String> paths);

  void unpublish(List<String> paths);

}
