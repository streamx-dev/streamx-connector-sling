package dev.streamx.sling.connector.handlers.resourcepath;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition
public @interface ClientlibsHandlerConfig {

  @AttributeDefinition(
      name = "Is Enabled?",
      description = "Indicates whether the configured service is enabled "
          + "and must undertake the supposed actions or not.",
      type = AttributeType.BOOLEAN,
      defaultValue = "true"
  )
  boolean enabled() default true;

  @AttributeDefinition(
      name = "Clientlibs Path - RexExp",
      description = "RegExp to match paths of clientlibs that should be published to StreamX.",
      type = AttributeType.STRING,
      defaultValue = "^/etc\\.clientlibs/.+"
  )
  String clientlibs_path_regexp() default "^/etc\\.clientlibs/.+";

  @AttributeDefinition(
      name = "Publications Channel Name",
      description = "Name of the channel in StreamX to publish clientlibs to.",
      type = AttributeType.STRING,
      defaultValue = "web-resources"
  )
  String publication_channel() default "web-resources";
}
