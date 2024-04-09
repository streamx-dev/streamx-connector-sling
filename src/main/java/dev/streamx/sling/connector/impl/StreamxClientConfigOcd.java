package dev.streamx.sling.connector.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "StreamX client configuration")
public @interface StreamxClientConfigOcd {

  @AttributeDefinition(name = "Name", description =
      "The client name, used to identify the client instance.")
  String name();

  @AttributeDefinition(name = "URL to StreamX", description =
      "URL to StreamX instance that will receive publication requests.")
  String streamxUrl();

  @AttributeDefinition(name = "JWT", description =
      "JWT that will be sent during publication requests.")
  String authToken();

  @AttributeDefinition(name = "Resource path patterns", description =
      "Patterns of the resource paths intended for publication on a given StreamX instance.")
  String[] resourcePathPatterns() default {".*"};

}
