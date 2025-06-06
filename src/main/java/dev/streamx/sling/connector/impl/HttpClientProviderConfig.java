package dev.streamx.sling.connector.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Configuration for {@link DefaultHttpClientFactory}.
 */
@ObjectClassDefinition(name = "HTTP client provider config")
public @interface HttpClientProviderConfig {

  /**
   * Default value for {@link #max_total()}.
   */
  int DEFAULT_NUMBER_OF_OPEN_CONNECTIONS = 50;

  /**
   * Default value for {@link #max_per_route()}.
   */
  int DEFAULT_MAX_PER_ROUTE_CONNECTIONS = 20;

  /**
   * Default value for {@link #connection_timeout()}.
   */
  int DEFAULT_CONNECTION_TIMEOUT_IN_MILLIS = 10000;

  /**
   * Default value for {@link #connection_request_timeout()}.
   */
  int DEFAULT_CONNECTION_REQUEST_TIMEOUT_IN_MILLIS = 10000;

  /**
   * Default value for {@link #socket_timeout()}.
   */
  int DEFAULT_SOCKET_TIMEOUT_IN_MILLIS = 5000;

  /**
   * Default value for {@link #idle_connection_keep_alive_time()}.
   */
  int DEFAULT_IDLE_CONNECTION_KEEP_ALIVE_TIME_IN_MILLIS = 30000;

  /**
   * Default value for {@link #keep_alive_time()}.
   */
  int DEFAULT_KEEP_ALIVE_TIME_IN_MILLIS = 60000;

  /**
   * Indicates if the client should trust all TLS certificates,
   * including self-signed and expired ones.
   * @return {@code true} if the client should trust all TLS certificates; {@code false} otherwise.
   */
  @AttributeDefinition(
      name = "Do trust all TLS certificates",
      description = "Indicates if the client should trust all TLS certificates, "
                  + "including self-signed and expired ones.",
      type = AttributeType.BOOLEAN,
      defaultValue = "false"
  )
  boolean insecure() default false;

  /**
   * Method that returns an int that contains the maximum number of total open connections.
   *
   * @return int that contains the maximum number of total open connections.
   */
  @AttributeDefinition(name = "Max Total", description = "The maximum number of total open connections")
  int max_total() default DEFAULT_NUMBER_OF_OPEN_CONNECTIONS;

  /**
   * Method that returns an int that contains the maximum number of concurrent connections per
   * route.
   *
   * @return int that contains the maximum number of concurrent connections per route.
   */
  @AttributeDefinition(name = "Max per route", description = "The maximum number of concurrent "
      + "connections per route")
  int max_per_route() default DEFAULT_MAX_PER_ROUTE_CONNECTIONS;

  /**
   * Method that returns an int that contains the time in seconds to establish the connection with
   * the remote host.
   *
   * @return int that contains the time in seconds to establish the connection with the remote host.
   */
  @AttributeDefinition(name = "Connection Timeout", description =
      "The time in milliseconds to establish the connection with the remote host")
  int connection_timeout() default DEFAULT_CONNECTION_TIMEOUT_IN_MILLIS;

  /**
   * Method that returns an int that contains the time to wait for a connection from the connection
   * manager/pool.
   *
   * @return int that contains the time to wait for a connection from the connection manager/pool.
   */
  @AttributeDefinition(name = "Connection Request Timeout ", description =
      "The time to wait for a connection from the connection manager/pool in milliseconds.")
  int connection_request_timeout() default DEFAULT_CONNECTION_REQUEST_TIMEOUT_IN_MILLIS;

  /**
   * Method that returns an int that contains the time waiting for data – after establishing the
   * connection; maximum time of inactivity between two data packets in milliseconds.
   *
   * @return int that contains the time waiting for data – after establishing the connection.
   */
  @AttributeDefinition(name = "Socket Timeout", description =
      "The time waiting for data – after establishing "
          + "the connection; maximum time of inactivity between two data packets in milliseconds.")
  int socket_timeout() default DEFAULT_SOCKET_TIMEOUT_IN_MILLIS;

  /**
   * Method that returns an int that contains the keep alive time for connection in milliseconds.
   *
   * @return int that contains the keep alive time for connection in milliseconds.
   */
  @AttributeDefinition(name = "Keep alive time", description = "Connection keep alive time in milliseconds")
  int keep_alive_time() default DEFAULT_KEEP_ALIVE_TIME_IN_MILLIS;

  /**
   * Method that returns an int that contains the time to keep idle connection alive in
   * milliseconds.
   *
   * @return int that contains the time to keep idle connection alive in milliseconds.
   */
  @AttributeDefinition(name = "Idle connection keep alive time", description =
      "The time to keep idle connection alive in milliseconds")
  int idle_connection_keep_alive_time() default DEFAULT_IDLE_CONNECTION_KEEP_ALIVE_TIME_IN_MILLIS;

}
