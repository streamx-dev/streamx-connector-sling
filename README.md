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
      "name": "streamx-instance",
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
      "name": "streamx-instance",
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

### Publication Jobs

Publication events are sent
using [Apache Sling Jobs](https://sling.apache.org/documentation/bundles/apache-sling-eventing-and-job-handling.html#jobs-guarantee-of-processing).
Events are added to the queue named `dev/streamx/publications`, which is managed by
the `Apache Sling Job Default Queue`. However, it is possible to define a custom Job Handler. For
example:

```json
{
  "configurations": {
    "org.apache.sling.event.jobs.QueueConfiguration~streamx-publication-actions": {
      "queue.name": "StreamX Publication Action Queue",
      "queue.topics": [
        "dev/streamx/publications"
      ],
      "queue.type": "UNORDERED",
      "queue.retries": 60,
      "queue.maxparallel": 0.5,
      "service.ranking": 1
    }
  }
}
```

#### Retry delay policy

Additionally, it's possible to define
a [PublicationRetryPolicy](./src/main/java/dev/streamx/sling/connector/PublicationRetryPolicy.java)
A custom implementation can be provided, but by default, the `DefaultPublicationRetryPolicy` is
used. This policy implementation has its default configuration, but can be customized with an OSGI
configuration as follows:

```json
{
  "configurations": {
    "dev.streamx.sling.connector.impl.DefaultPublicationRetryPolicy": {
      "retry.delay": 2000,
      "retry.multiplication": 2,
      "max.retry.delay": 60000
    }
  }
}
```

## HttpClient

By default, module will use its own CloseableHttpClient based on the following configuration:
[HttpClientProviderConfig](./src/main/java/dev/streamx/sling/connector/impl/HttpClientProviderConfig.java).
If needed, clients can provide custom CloseableHttpClient by implementing
[HttpClientFactory](./src/main/java/dev/streamx/sling/connector/HttpClientFactory.java) interface.

## Related resources

Publication of some resources may arise a necessity to refresh the content of associated resources
when certain resources are published. An illustrative scenario is a blog articles list dependent on
published blog article pages. While it's possible to manually publish pages containing
the blog list each time a new article is published, we've introduced
the [RelatedResourcesSelector](./src/main/java/dev/streamx/sling/connector/RelatedResourcesSelector.java)
interface to automate this process.

Its implementation is intended to provide a list of resource paths along with the corresponding
action to be taken when a particular resource is published.

### Default Related Resources Selector implementation

The connector includes a built-in implementation of [RelatedResourcesSelector](./src/main/java/dev/streamx/sling/connector/RelatedResourcesSelector.java),
called [ResourceContentRelatedResourcesSelector](./src/main/java/dev/streamx/sling/connector/selectors/content/ResourceContentRelatedResourcesSelector.java).

The `getRelatedResources(String resourcePath)` method of `ResourceContentRelatedResourcesSelector` makes an internal Sling request to retrieve the content of the specified resource.
It scans that content to identify and collect related resources referenced within it.
A typical example is extracting links from HTML elements like `<img src=...>`, `<script src=...>` and similar.

This service is intended for scenarios where you need to collect and publish resource paths—such as images, scripts, etc.—that are referenced within a page but aren't published to StreamX separately. For example, if a page references other pages and those pages are already being published individually by content authors, you should not use this mechanism to publish the page links.

Example configuration for finding referenced clientlibs and core images in a page content:
```json
{
  "configurations": {
    "dev.streamx.sling.connector.selectors.content.ResourceContentRelatedResourcesSelector~pages": {
      "references.search-regexes":"$[env:STREAMX_REFERENCES_SEARCH_REGEXES;type=String[];delimiter=,;default=(/content[^\"'\\s]*\\.coreimg\\.[^\"'\\s]*),(/[^\"'\\s]*etc\\.clientlibs[^\"'\\s]*)]",
      "references.exclude-from-result.regex":".*\\{\\.width\\}.*",
      "resource-path.postfix-to-append":".html",
      "resource.required-path.regex":"^/content/.*",
      "resource.required-primary-node-type.regex":"cq:Page"
    }
  }
}
```

Refer to the [@AttributeDefinition of the fields in ](./src/main/java/dev/streamx/sling/connector/selectors/content/ResourceContentRelatedResourcesSelectorConfig.java) for more information on their usage.

To use `ResourceContentRelatedResourcesSelector`, you must also provide an implementation of [ResourcePathPublicationHandler](./src/main/java/dev/streamx/sling/connector/handlers/resourcepath/ResourcePathPublicationHandler.java).
This handler is responsible for actually publishing or unpublishing the related resources identified by the selector.

The `ResourcePathPublicationHandler` implementation requires providing settings such as `resourcePathRegex` and `channel` for ingesting data, as defined in the [ResourcePathPublicationHandlerConfig](./src/main/java/dev/streamx/sling/connector/handlers/resourcepath/ResourcePathPublicationHandlerConfig.java).

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