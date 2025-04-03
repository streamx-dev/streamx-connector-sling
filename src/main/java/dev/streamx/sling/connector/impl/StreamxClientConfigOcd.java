package dev.streamx.sling.connector.impl;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Configuration for {@link StreamxClientConfigImpl}.
 */
@ObjectClassDefinition(name = StreamxClientConfigImpl.SERVICE_DESCRIPTION)
public @interface StreamxClientConfigOcd {

  /**
   * Returns the StreamX client name, used to identify the client instance.
   *
   * @return StreamX client name, used to identify the client instance
   */
  @AttributeDefinition(
      name = "Name",
      description = "StreamX client name, used to identify the client instance.",
      type = AttributeType.STRING,
      defaultValue = "streamx-basic-instance"
  )
  String name() default "streamx-basic-instance";

  /**
   * Returns the URL to StreamX REST Ingestion Service that will receive publish and unpublish
   * requests.
   *
   * @return URL to StreamX REST Ingestion Service that will receive publish and unpublish requests
   */
  @AttributeDefinition(
      name = "URL to StreamX",
      description = "URL to StreamX REST Ingestion Service that will "
          + "receive publish and unpublish requests.",
      type = AttributeType.STRING,
      defaultValue = "http://localhost:8080"
  )
  String streamxUrl() default "http://localhost:8080";

  /**
   * Returns the JWT that will be sent along with publish and unpublish requests.
   *
   * @return JWT that will be sent along with publish and unpublish requests
   */
  @AttributeDefinition(
      name = "JWT",
      description = "JWT that will be sent along with publish and unpublish requests.",
      type = AttributeType.PASSWORD,
      defaultValue = StringUtils.EMPTY
  )
  String authToken() default StringUtils.EMPTY;

  /**
   * Returns the regex patterns of the resource paths intended for publication and unpublication
   * to/from a StreamX instance.
   *
   * @return regex patterns of the resource paths intended for publication and unpublication
   * to/from a StreamX instance
   */
  @AttributeDefinition(
      name = "Resource path regex patterns",
      description = "Regex patterns of the resource paths intended for "
          + "publication and unpublication to/from a StreamX instance.",
      type = AttributeType.STRING,
      defaultValue = ".*"
  )
  String[] resourcePathPatterns() default {".*"};

}
