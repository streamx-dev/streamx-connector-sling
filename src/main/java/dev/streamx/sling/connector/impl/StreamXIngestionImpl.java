package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_ACTION;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_CLIENT_NAME;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_HANDLER_ID;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PATH;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.JOB_TOPIC;

import dev.streamx.sling.connector.IngestionActionType;
import dev.streamx.sling.connector.IngestionDataFactory;
import dev.streamx.sling.connector.RelatedResource;
import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.StreamXIngestion;
import dev.streamx.sling.connector.impl.StreamXIngestionImpl.Config;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
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
  private RelatedResourcesSelectorRegistry relatedResourcesSelectorRegistry;

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
  public void publish(List<String> pathsToPublish) throws StreamxPublicationException {
    handlePublication(IngestionActionType.PUBLISH, pathsToPublish);
  }

  @Override
  public void unpublish(List<String> pathsToUnpublish) throws StreamxPublicationException {
    handlePublication(IngestionActionType.UNPUBLISH, pathsToUnpublish);
  }

  private void handlePublication(IngestionActionType ingestionActionType, List<String> resourcesPaths)
      throws StreamxPublicationException {
    LOG.trace("Handling publication for paths: {}", resourcesPaths);
    if (!enabled || resourcesPaths.isEmpty()) {
      return;
    }

    Set<RelatedResource> relatedResources = findRelatedResources(resourcesPaths, ingestionActionType);

    try {
      handleResourcesPublication(resourcesPaths, ingestionActionType);
      handleRelatedResourcesPublication(relatedResources);
    } catch (JobCreationException e) {
      throw new StreamxPublicationException("Can't handle publication. " + e.getMessage());
    }
  }

  private Set<RelatedResource> findRelatedResources(List<String> resourcesPaths,
      IngestionActionType ingestionActionType)
      throws StreamxPublicationException {
    LOG.trace("Searching for related resources for {} and these paths: {}", ingestionActionType, resourcesPaths);
    Set<RelatedResource> relatedResources = new LinkedHashSet<>();
    for (String resourcePath : resourcesPaths) {
      relatedResources.addAll(findRelatedResources(resourcePath, ingestionActionType));
    }

    Predicate<RelatedResource> shouldBePublished = shouldPublishResourcePredicate(resourcesPaths,
        ingestionActionType);
    return relatedResources.stream().filter(shouldBePublished)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private void handleResourcesPublication(List<String> resourcesPaths, IngestionActionType ingestionActionType)
      throws JobCreationException {
    for (String resourcePath : resourcesPaths) {
      if (StringUtils.isBlank(resourcePath)) {
        continue;
      }

      handlePublication(resourcePath, ingestionActionType);
    }
  }

  private void handlePublication(String resourcePath, IngestionActionType ingestionActionType)
      throws JobCreationException {
    LOG.trace("Handling publication for resource: {}", resourcePath);
    for (IngestionDataFactory<?> handler : publicationHandlerRegistry.getHandlers()) {
      if (handler.canProduce(() -> resourcePath)) {
        addPublicationToQueue(handler.getId(), ingestionActionType, resourcePath);
      }
    }
  }

  private void handleRelatedResourcesPublication(Set<RelatedResource> relatedResources)
      throws JobCreationException {
    for (RelatedResource relatedResource : relatedResources) {
      handlePublication(relatedResource.getResourcePath(), relatedResource.getIngestionActionType());
    }
  }

  private Predicate<RelatedResource> shouldPublishResourcePredicate(List<String> publishedResources,
      IngestionActionType ingestionActionType) {
    return relatedResource -> !isPublished(relatedResource, publishedResources, ingestionActionType);
  }

  private boolean isPublished(RelatedResource relatedResource, List<String> publishedResources,
      IngestionActionType ingestionActionType) {
    return relatedResource.getIngestionActionType().equals(ingestionActionType) && publishedResources.contains(
        relatedResource.getResourcePath());
  }

  private Set<RelatedResource> findRelatedResources(String resourcePath, IngestionActionType ingestionActionType)
      throws StreamxPublicationException {
    Set<RelatedResource> relatedResources = new LinkedHashSet<>();
    for (RelatedResourcesSelector relatedResourcesSelector : relatedResourcesSelectorRegistry.getSelectors()) {
      relatedResources.addAll(relatedResourcesSelector.getRelatedResources(resourcePath, ingestionActionType));
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
    jobProperties.put(PN_STREAMX_ACTION, ingestionActionType.toString());
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
