package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.ResourceData;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.sling.event.jobs.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("StaticMethodOnlyUsedInOneClass")
class JobAsResourceData implements ResourceData {

  static final String JOB_TOPIC = "dev/streamx/ingestion-trigger";
  private static final String PN_STREAMX_INGESTION_ACTION = "streamx.ingestionAction";
  private static final String PN_STREAMX_URI_TO_INGEST = "streamx.uriToIngest";
  private static final Logger LOG = LoggerFactory.getLogger(JobAsResourceData.class);
  private final Supplier<ResourceData> ingestedData;
  private final PublicationAction publicationAction;

  JobAsResourceData(ResourceData resourceData, PublicationAction publicationAction) {
    this.ingestedData = () -> resourceData;
    this.publicationAction = publicationAction;
  }

  @Override
  public String toString() {
    return String.format(
        "{%s: uri{%s}, action{%s}, props{%s}}",
        this.getClass().getSimpleName(), resourcePath(), ingestionAction(), properties()
    );
  }

  JobAsResourceData(Job job) {
    this.publicationAction = PublicationAction.of(
        job.getProperty(PN_STREAMX_INGESTION_ACTION, String.class)
    ).orElseThrow();
    this.ingestedData = () -> new ResourceData() {

      @Override
      public String resourcePath() {
        return Optional.ofNullable(job.getProperty(PN_STREAMX_URI_TO_INGEST, String.class))
            .orElseThrow();
      }

      @Override
      public Map<String, Object> properties() {
        String exclusionRegex = String.format(
            "%s|%s", PN_STREAMX_INGESTION_ACTION, PN_STREAMX_URI_TO_INGEST
        );
        Map<String, Object> extractedProps = job.getPropertyNames().stream()
            .filter(propertyName -> !propertyName.matches(exclusionRegex))
            .map(
                propertyName -> Map.entry(
                    propertyName,
                    job.getProperty(propertyName)
                )
            ).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        LOG.trace(
            "Out of {} extracted {} props. Exclusion regex: '{}'",
            job, extractedProps, exclusionRegex
        );
        return extractedProps;
      }
    };
  }

  PublicationAction ingestionAction() {
    return publicationAction;
  }

  @Override
  public String resourcePath() {
    return ingestedData.get().resourcePath();
  }

  @Override
  public Map<String, Object> properties() {
    return ingestedData.get().properties();
  }

  @SuppressWarnings({"squid:S3599", "squid:S1171"})
  Map<String, Object> asJobProps() {
    Map<String, Object> basicProps = Map.of(
        PN_STREAMX_INGESTION_ACTION,
        ingestionAction().toString(),
        PN_STREAMX_URI_TO_INGEST,
        resourcePath()
    );
    return Collections.unmodifiableMap(
        new HashMap<>() {{
          putAll(basicProps);
          putAll(properties());
        }}
    );
  }
}
