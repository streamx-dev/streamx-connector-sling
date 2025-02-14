package dev.streamx.sling.connector.impl;

import java.util.List;

/**
 * See {@link StreamxClientConfigImpl#SERVICE_DESCRIPTION}.
 */
public interface StreamxClientConfig {

  /**
   * Returns the StreamX client name, used to identify the client instance.
   * @return StreamX client name, used to identify the client instance
   */
  String getName();

  /**
   * Returns the URL to StreamX REST Ingestion Service that
   * will receive publish and unpublish requests.
   * @return URL to StreamX REST Ingestion Service that will receive publish and unpublish requests
   */
  String getStreamxUrl();

  /**
   * Returns the JWT that will be sent along with publish and unpublish requests.
   * @return JWT that will be sent along with publish and unpublish requests
   */
  String getAuthToken();

  /**
   * Returns the regex patterns of the resource paths intended for publication and unpublication
   * to/from a StreamX instance.
   * @return regex patterns of the resource paths intended for publication and unpublication
   * to/from a StreamX instance
   */
  List<String> getResourcePathPatterns();

}
