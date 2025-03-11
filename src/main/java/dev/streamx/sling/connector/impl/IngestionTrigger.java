package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.PublicationAction;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.api.uri.SlingUriBuilder;
import org.apache.sling.event.jobs.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Trigger to ingest data into Streamx.
 */
@SuppressWarnings("StaticMethodOnlyUsedInOneClass")
class IngestionTrigger {

  static final String JOB_TOPIC = "dev/streamx/ingestion-trigger";
  private static final String PN_STREAMX_INGESTION_ACTION = "streamx.ingestionAction";
  private static final String PN_STREAMX_URIS_TO_INGEST = "streamx.urisToIngest";
  private static final Logger LOG = LoggerFactory.getLogger(IngestionTrigger.class);
  private final PublicationAction ingestionAction;
  private final List<SlingUri> urisToIngest;

  /**
   * Constructs an instance of this class.
   *
   * @param job                     {@link Job} that should be converted into the newly created
   *                                {@link IngestionTrigger}
   * @param resourceResolverFactory {@link ResourceResolverFactory} to be used by the newly created
   *                                {@link IngestionTrigger}
   */
  IngestionTrigger(Job job, ResourceResolverFactory resourceResolverFactory) {
    LOG.trace("Decomposing {}", job);
    String ingestionActionRaw = job.getProperty(PN_STREAMX_INGESTION_ACTION, String.class);
    this.ingestionAction = PublicationAction.of(ingestionActionRaw).orElseThrow();
    String[] urisToIngestRaw = job.getProperty(PN_STREAMX_URIS_TO_INGEST, String[].class);
    this.urisToIngest = Stream.of(urisToIngestRaw)
        .flatMap(rawUri -> toSlingUri(rawUri, resourceResolverFactory).stream())
        .collect(Collectors.toUnmodifiableList());
    LOG.trace("Decomposed {} into [{} {}]", job, ingestionAction, urisToIngest);
  }

  /**
   * Constructs an instance of this class.
   *
   * @param ingestionAction {@link PublicationAction} to be performed as the result of this
   *                        {@link IngestionTrigger} activation
   * @param urisToIngest    {@link List} of {@link SlingUri}s to be ingested as the result of this
   *                        {@link IngestionTrigger} activation
   */
  IngestionTrigger(PublicationAction ingestionAction, List<SlingUri> urisToIngest) {
    this.ingestionAction = ingestionAction;
    this.urisToIngest = Collections.unmodifiableList(urisToIngest);
  }

  /**
   * Returns the {@link PublicationAction} to be performed as the result of this
   * {@link IngestionTrigger} activation.
   *
   * @return {@link PublicationAction} to be performed as the result of this
   * {@link IngestionTrigger}
   */
  PublicationAction ingestionAction() {
    return ingestionAction;
  }

  /**
   * {@link List} of {@link SlingUri}s to be ingested as the result of this {@link IngestionTrigger}
   * activation
   *
   * @return {@link List} of {@link SlingUri}s to be ingested as the result of this
   * {@link IngestionTrigger} activation
   */
  List<SlingUri> urisToIngest() {
    return urisToIngest;
  }

  @SuppressWarnings("deprecation")
  private Optional<SlingUri> toSlingUri(
      String rawUri, ResourceResolverFactory resourceResolverFactory
  ) {
    try (
        ResourceResolver resourceResolver
            = resourceResolverFactory.getAdministrativeResourceResolver(null)
    ) {
      return Optional.ofNullable(rawUri)
          .map(
              rawUriNonNull -> {
                SlingUri slingUri = SlingUriBuilder.parse(rawUriNonNull, resourceResolver).build();
                LOG.trace("Parsed URI: {}", slingUri);
                return slingUri;
              }
          );
    } catch (LoginException exception) {
      String message = String.format("Unable to parse URI: '%s'", rawUri);
      LOG.error(message, exception);
      return Optional.empty();
    }
  }

  /**
   * Returns properties of this {@link IngestionTrigger} out of which a new {@link Job} representing
   * this {@link IngestionTrigger} can be created.
   *
   * @return properties of this {@link IngestionTrigger} out of which a new {@link Job} representing
   * this {@link IngestionTrigger} can be created
   */
  @SuppressWarnings("unused")
  Map<String, Object> asJobProps() {
    return Map.of(
        PN_STREAMX_INGESTION_ACTION,
        ingestionAction.toString(),
        PN_STREAMX_URIS_TO_INGEST,
        urisToIngest.stream().map(SlingUri::toString).toArray(String[]::new)
    );
  }
}
