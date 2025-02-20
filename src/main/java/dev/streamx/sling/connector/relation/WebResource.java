package dev.streamx.sling.connector.relation;

import java.io.InputStream;

interface WebResource {

  InputStream content();

  StreamXChannelName destinationChannel();

  Class<?> model();

}
