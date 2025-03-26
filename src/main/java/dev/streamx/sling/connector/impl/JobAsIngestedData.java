package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.IngestedData;
import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.util.DefaultSlingUriBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.event.jobs.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("StaticMethodOnlyUsedInOneClass")
class JobAsIngestedData implements IngestedData {

  static final String JOB_TOPIC = "dev/streamx/ingestion-trigger";
  private static final String PN_STREAMX_INGESTION_ACTION = "streamx.ingestionAction";
  private static final String PN_STREAMX_URI_TO_INGEST = "streamx.uriToIngest";
  private static final Logger LOG = LoggerFactory.getLogger(JobAsIngestedData.class);
  private final Supplier<IngestedData> ingestedData;

  JobAsIngestedData(IngestedData ingestedData) {
    this.ingestedData = () -> ingestedData;
  }

  JobAsIngestedData(Job job, ResourceResolverFactory resourceResolverFactory) {
    this.ingestedData = () -> new IngestedData() {
      @Override
      public PublicationAction ingestionAction() {
        return PublicationAction.of(
            job.getProperty(PN_STREAMX_INGESTION_ACTION, String.class)
        ).orElseThrow();
      }

      @Override
      public SlingUri uriToIngest() {
        return new DefaultSlingUriBuilder(
            job.getProperty(PN_STREAMX_URI_TO_INGEST, String.class),
            resourceResolverFactory
        ).build();
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
            "Out of {} extracted {}. Exclusion regex: '{}'", job, extractedProps, exclusionRegex
        );
        return extractedProps;
      }
    };
  }

  @Override
  public PublicationAction ingestionAction() {
    return ingestedData.get().ingestionAction();
  }

  @Override
  public SlingUri uriToIngest() {
    return ingestedData.get().uriToIngest();
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
        uriToIngest().toString()
    );
    return Collections.unmodifiableMap(
        new HashMap<>() {{
          putAll(basicProps);
          putAll(properties());
        }}
    );
  }
}
