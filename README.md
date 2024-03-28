# Sling StreamX Connector

This module gives the possibility for Sling-based projects to publish and unpublish data to/from
StreamX.

# API Usage

Clients need to implement
[PublicationHandler](./src/main/java/dev/streamx/sling/connector/PublicationHandler.java)
interface to handle various types of content such as pages and assets.
Then they can use
[StreamxPublicationService](./src/main/java/dev/streamx/sling/connector/StreamxPublicationService.java)
to make the actual publication of a resource.

## Module Configuration

The publication is enabled by default, but can be disabled with a config.

Module requires setting up StreamX's ingestion client.
Example:

```json
{
  "configurations": {
    "dev.streamx.sling.connector.impl.StreamxClientConfigImpl~streamx-instance": {
      "streamxUrl": "$[env:STREAMX_URL]",
      "authToken": "$[secret:STREAMX_AUTH_TOKEN]"
    }
  }
}
```

Optionally, client configuration can contain `resourcePathPatterns` parameter. It defines patterns
of the resource paths intended for publication on a given StreamX instance.

By default, it's set to `[".*"]` which means that all the resources will be published.

Example configuration containing custom resourcePathPatterns:

```json
{
  "configurations": {
    "dev.streamx.sling.connector.impl.StreamxClientConfigImpl~streamx-instance": {
      "streamxUrl": "$[env:STREAMX_URL]",
      "authToken": "$[secret:STREAMX_AUTH_TOKEN]",
      "resourcePathPatterns": [
        "/.*/my-page-space/.*",
        "/libs/my-app/.*"
      ]
    }
  }
}

```

## HttpClient

By default, module will use its own CloseableHttpClient based on the following configuration:
[HttpClientProviderConfig](./src/main/java/dev/streamx/sling/connector/impl/HttpClientProviderConfig.java).
If needed, clients can provide custom CloseableHttpClient by implementing
[HttpClientFactory](./src/main/java/dev/streamx/sling/connector/HttpClientFactory.java) interface.

# Usage:

Build

```
mvn clean install
```

To deploy bundle to default Sling instance on localhost:4502, run

```
mvn clean install -PautoInstallBundle
```

To set host and port directly, use

```
mvn clean install -PautoInstallBundle -Dsling.host=localhost -Dsling.port=4503
```