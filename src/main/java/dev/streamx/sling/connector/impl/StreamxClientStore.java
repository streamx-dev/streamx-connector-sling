package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.ResourceInfo;
import java.util.List;

/**
 * Stores {@link StreamxInstanceClient} instances.
 */
interface StreamxClientStore {

  /**
   * Returns all {@link StreamxInstanceClient} instances related to the specified path.
   * @param resourceInfo resource to get {@link StreamxInstanceClient}s for
   * @return list of {@link StreamxInstanceClient}s related to the specified path
   */
  List<StreamxInstanceClient> getForResource(ResourceInfo resourceInfo);

  /**
   * Returns the {@link StreamxInstanceClient} instance with the specified name.
   * @param name name of the {@link StreamxInstanceClient} to get
   * @return the {@link StreamxInstanceClient} with the specified name
   */
  StreamxInstanceClient getByName(String name);

}
