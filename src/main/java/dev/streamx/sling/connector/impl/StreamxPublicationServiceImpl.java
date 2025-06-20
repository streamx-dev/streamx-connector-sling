package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_ACTION;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_CLIENT_NAME;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_HANDLER_ID;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PATH;

import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.StreamxPublicationService;
import dev.streamx.sling.connector.impl.StreamxPublicationServiceImpl.Config;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link StreamxPublicationService}.
 */
@Component(
    service = {StreamxPublicationService.class, JobExecutor.class},
    property = JobExecutor.PROPERTY_TOPICS + "=" + IngestionTriggerJobHelper.JOB_TOPIC,
    immediate = true
)
@Designate(ocd = Config.class)
public class StreamxPublicationServiceImpl implements StreamxPublicationService, JobExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(StreamxPublicationServiceImpl.class);

  @Reference
  private JobManager jobManager;

  @Reference
  private PublicationHandlerRegistry publicationHandlerRegistry;

  @Reference
  private RelatedResourcesSelectorRegistry relatedResourcesSelectorRegistry;

  @Reference
  private StreamxClientStore streamxClientStore;

  private boolean enabled;

  /**
   * Constructs an instance of this class.
   */
  public StreamxPublicationServiceImpl() {
  }

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
  public void publish(List<ResourceInfo> resourcesToPublish) throws StreamxPublicationException {
    submitIngestionTriggerJob(PublicationAction.PUBLISH, resourcesToPublish);
  }

  @Override
  public void unpublish(List<ResourceInfo> resourcesToUnpublish) throws StreamxPublicationException {
    submitIngestionTriggerJob(PublicationAction.UNPUBLISH, resourcesToUnpublish);
  }

  private void submitIngestionTriggerJob(PublicationAction ingestionAction, List<ResourceInfo> resources) {
    Map<String, Object> jobProps = IngestionTriggerJobHelper.asJobProps(ingestionAction, resources);
    Job addedJob = jobManager.addJob(IngestionTriggerJobHelper.JOB_TOPIC, jobProps);
    LOG.debug("Added job: {}", addedJob);
  }

  @Override
  public JobExecutionResult process(Job job, JobExecutionContext jobExecutionContext) {
    LOG.trace("Processing {}", job);
    PublicationAction ingestionAction = IngestionTriggerJobHelper.extractPublicationAction(job);
    List<ResourceInfo> resources = IngestionTriggerJobHelper.extractResourcesInfo(job);
    try {
      handlePublication(ingestionAction, resources);
      return jobExecutionContext.result().succeeded();
    } catch (StreamxPublicationException exception) {
      LOG.error("Error while processing job", exception);
      return jobExecutionContext.result().message("Error while processing job: " + exception.getMessage()).failed();
    }
  }

  private void handlePublication(PublicationAction action, List<ResourceInfo> resources)
      throws StreamxPublicationException {
    LOG.trace("Handling publication for paths: {}", resources);
    if (!enabled || resources.isEmpty()) {
      return;
    }

    try {
      handleResourcesPublication(resources, action);
      if (action == PublicationAction.PUBLISH) {
        Set<ResourceInfo> relatedResources = findRelatedResources(resources);
        publishRelatedResources(relatedResources);
      }
    } catch (JobCreationException e) {
      throw new StreamxPublicationException("Can't handle publication. " + e.getMessage(), e);
    }
  }

  private Set<ResourceInfo> findRelatedResources(List<ResourceInfo> parentResources) {
    Set<String> parentResourcesPaths = parentResources.stream()
        .map(ResourceInfo::getPath)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    LOG.trace("Searching for related resources for paths: {}", parentResourcesPaths);

    return parentResourcesPaths.stream()
        .flatMap(resourcePath -> findRelatedResources(resourcePath).stream())
        .filter(relatedResource -> !parentResourcesPaths.contains(relatedResource.getPath()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private void handleResourcesPublication(List<ResourceInfo> resources, PublicationAction action)
      throws JobCreationException {
    for (ResourceInfo resource : resources) {
      if (StringUtils.isBlank(resource.getPath())) {
        continue;
      }
      handlePublication(resource, action);
    }
  }

  private void handlePublication(ResourceInfo resource, PublicationAction action)
      throws JobCreationException {
    LOG.trace("Handling publication for resource: {}", resource);
    for (PublicationHandler<?> handler : publicationHandlerRegistry.getHandlers()) {
      if (handler.canHandle(resource)) {
        addPublicationToQueue(handler.getId(), action, resource.getPath());
      }
    }
  }

  private void publishRelatedResources(Set<ResourceInfo> relatedResources) throws JobCreationException {
    for (ResourceInfo relatedResource : relatedResources) {
      LOG.trace("Handling related resource publication: {}", relatedResource);
      handlePublication(relatedResource, PublicationAction.PUBLISH);
    }
  }

  private Set<ResourceInfo> findRelatedResources(String parentResourcePath) {
    Set<ResourceInfo> relatedResources = new LinkedHashSet<>();
    for (RelatedResourcesSelector relatedResourcesSelector : relatedResourcesSelectorRegistry.getSelectors()) {
      relatedResources.addAll(relatedResourcesSelector.getRelatedResources(parentResourcePath));
    }
    return relatedResources;
  }

  private void addPublicationToQueue(String handlerId, PublicationAction action,
      String resourcePath) throws JobCreationException {
    LOG.debug("Adding publication request for [{}: {}] to queue", handlerId, resourcePath);
    for (StreamxInstanceClient client : streamxClientStore.getForResource(resourcePath)) {
      addPublicationToQueue(handlerId, action, resourcePath, client.getName());
    }
  }

  private void addPublicationToQueue(String handlerId, PublicationAction action,
      String resourcePath,
      String clientName) throws JobCreationException {
    Map<String, Object> jobProperties = new HashMap<>();
    jobProperties.put(PN_STREAMX_HANDLER_ID, handlerId);
    jobProperties.put(PN_STREAMX_CLIENT_NAME, clientName);
    jobProperties.put(PN_STREAMX_ACTION, action.toString());
    jobProperties.put(PN_STREAMX_PATH, resourcePath);
    Job job = jobManager.addJob(PublicationJobExecutor.JOB_TOPIC, jobProperties);
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
