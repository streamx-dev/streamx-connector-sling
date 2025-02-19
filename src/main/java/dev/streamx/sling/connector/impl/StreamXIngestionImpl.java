package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.PublicationJobExecutor.JOB_TOPIC;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_CLIENT_NAME;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_HANDLER_ID;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_INGESTION_TYPE;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PATH;

import dev.streamx.sling.connector.IngestionActionType;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.RelatedDataSearch;
import dev.streamx.sling.connector.StreamXIngestion;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.impl.StreamXIngestionImpl.Config;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    service = StreamXIngestion.class
)
@Designate(ocd = Config.class)
public class StreamXIngestionImpl implements StreamXIngestion {

  private static final Logger LOG = LoggerFactory.getLogger(StreamXIngestionImpl.class);

  @Reference
  private JobManager jobManager;

  @Reference
  private PublicationHandlerRegistry publicationHandlerRegistry;

  @Reference
  private RelatedDataSearches relatedDataSearches;

  @Reference
  private StreamxClientStore streamxClientStore;

  private boolean enabled;

  @Activate
  @Modified
  private void activate(Config config) {
    enabled = config.enabled();
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void publish(List<String> keys) throws StreamxPublicationException {
    ingest(IngestionActionType.PUBLISH, keys);
  }

  @Override
  public void unpublish(List<String> keys) throws StreamxPublicationException {
    ingest(IngestionActionType.UNPUBLISH, keys);
  }

  private void ingest(IngestionActionType ingestionActionType, List<String> resourcesPaths)
      throws StreamxPublicationException {
    //TODO: exclude already published resources
    LOG.trace("Handling publication for paths: {}", resourcesPaths);
    if (!enabled || resourcesPaths.isEmpty()) {
      return;
    }
    Collection<String> relatedResources = findRelatedResources(resourcesPaths);
    try {
      handleResourcesPublication(resourcesPaths, ingestionActionType);
      ingest(
          ingestionActionType, relatedResources.stream().collect(Collectors.toUnmodifiableList())
      );
    } catch (JobCreationException e) {
      throw new StreamxPublicationException("Can't handle publication. " + e.getMessage());
    }
  }

  private Collection<String> findRelatedResources(List<String> resourcesPaths) {
    LOG.trace("Searching for related resources for: {}", resourcesPaths);
    Collection<String> relatedResources = new LinkedHashSet<>();
    for (String resourcePath : resourcesPaths) {
      relatedResources.addAll(findRelatedResources(resourcePath));
    }
    return relatedResources;
  }

  private void handleResourcesPublication(Iterable<String> resourcesPaths,
      IngestionActionType ingestionType)
      throws JobCreationException {
    for (String resourcePath : resourcesPaths) {
      if (StringUtils.isBlank(resourcePath)) {
        continue;
      }

      ingest(resourcePath, ingestionType);
    }
  }

  private void ingest(String resourcePath, IngestionActionType ingestionType)
      throws JobCreationException {
    LOG.trace("Handling publication for resource: {}", resourcePath);
    for (PublicationHandler<?> handler : publicationHandlerRegistry.getHandlers()) {
      if (handler.canHandle(resourcePath)) {
        addPublicationToQueue(handler.getId(), ingestionType, resourcePath);
      }
    }
  }

  private Set<String> findRelatedResources(String resourcePath) {
    Set<String> relatedResources = new LinkedHashSet<>();
    for (RelatedDataSearch relatedDataSearch : relatedDataSearches.searches()) {
      relatedResources.addAll(relatedDataSearch.find(resourcePath));
    }
    return relatedResources;
  }

  private void addPublicationToQueue(String handlerId, IngestionActionType ingestionActionType,
      String resourcePath) throws JobCreationException {
    LOG.debug("Adding publication request for [{}: {}] to queue", handlerId, resourcePath);
    for (StreamxInstanceClient client : streamxClientStore.getForResource(resourcePath)) {
      addPublicationToQueue(handlerId, ingestionActionType, resourcePath, client.getName());
    }
  }

  private void addPublicationToQueue(String handlerId, IngestionActionType ingestionActionType,
      String resourcePath,
      String clientName) throws JobCreationException {
    Map<String, Object> jobProperties = new HashMap<>();
    jobProperties.put(PN_STREAMX_HANDLER_ID, handlerId);
    jobProperties.put(PN_STREAMX_CLIENT_NAME, clientName);
    jobProperties.put(PN_STREAMX_INGESTION_TYPE, ingestionActionType.toString());
    jobProperties.put(PN_STREAMX_PATH, resourcePath);
    Job job = jobManager.addJob(JOB_TOPIC, jobProperties);
    if (job == null) {
      throw new JobCreationException("Publication job could not be created by JobManager");
    }
    LOG.debug(
        "Publication request for [{}: {}] added to queue. Job: {}", handlerId, resourcePath, job
    );
  }

  @ObjectClassDefinition(name = "StreamX Connector Configuration")
  @interface Config {

    @AttributeDefinition(name = "Enable publication to StreamX", description =
        "If the flag is unset the publication requests will be skipped.")
    boolean enabled() default true;
  }
}
