package dev.streamx.sling.connector.impl;

import java.util.List;

public interface StreamxClientStore {

  List<StreamxInstanceClient> getForResource(String resourcePath);

  StreamxInstanceClient getByName(String name);

}
