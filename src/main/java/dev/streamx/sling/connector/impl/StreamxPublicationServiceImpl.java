package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_ACTION;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_CLIENT_NAME;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_HANDLER_ID;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_PATH;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_PROPERTIES;

import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.StreamxPublicationService;
import dev.streamx.sling.connector.impl.StreamxPublicationServiceImpl.Config;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
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

  @Reference
  private ResourceResolverFactory resourceResolverFactory;

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
    LOG.trace("Handling publication for paths: {}", resources);

    if (enabled && !resources.isEmpty()) {
      try (ResourceResolver resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver( null)) {
        handleResourcesPublication(ingestionAction, resources);
        handleRelatedResourcesPublication(ingestionAction, resources, resourceResolver);
      } catch (StreamxPublicationException exception) {
        LOG.error("Error while processing job", exception);
        return jobExecutionContext.result() .message("Error while processing job: " + exception.getMessage()).failed();
      } catch (LoginException | RepositoryException ex) {
        LOG.error("Error updating JCR state for related resources", ex);
      }
    }
    return jobExecutionContext.result().succeeded();
  }

  private void handleResourcesPublication(PublicationAction action, List<ResourceInfo> resources) throws StreamxPublicationException {
    try {
      for (ResourceInfo resource : resources) {
        handlePublication(resource, action);
      }
    } catch (Exception e) {
      throw new StreamxPublicationException("Can't handle publication of resources. " + e.getMessage(), e);
    }
  }

  private void handleRelatedResourcesPublication(PublicationAction action, List<ResourceInfo> resources, ResourceResolver resourceResolver)
      throws RepositoryException, StreamxPublicationException {
    try {
      Map<String, Set<ResourceInfo>> relatedResourcesMap = findRelatedResources(resources);
      Session session = getSession(resourceResolver);
      if (action == PublicationAction.PUBLISH) {
        Set<ResourceInfo> distinctRelatedResources = SetUtils.flattenToLinkedHashSet(relatedResourcesMap.values());
        publishRelatedResources(distinctRelatedResources, session);
        Map<String, Set<ResourceInfo>> disappearedRelatedResources = PublishedRelatedResourcesManager.updatePublishedResourcesData(relatedResourcesMap, session);
        unpublishRelatedResources(disappearedRelatedResources);
      } else if (action == PublicationAction.UNPUBLISH) {
        unpublishRelatedResources(relatedResourcesMap);
        PublishedRelatedResourcesManager.removePublishedResourcesData(resources, session);
      }
      if (session.hasPendingChanges()) {
        session.save();
      }
    } catch (RepositoryException ex) {
      throw ex;
    } catch (Exception e) {
      throw new StreamxPublicationException("Can't handle publication of related resources. " + e.getMessage(), e);
    }
  }

  private Map<String, Set<ResourceInfo>> findRelatedResources(List<ResourceInfo> parentResources) {
    LOG.trace("Searching for related resources for {}", parentResources);
    Set<String> parentResourcesPaths = SetUtils.mapToLinkedHashSet(parentResources, ResourceInfo::getPath);

    Map<String, Set<ResourceInfo>> result = new LinkedHashMap<>();
    for (ResourceInfo parentResource : parentResources) {
      Set<ResourceInfo> relatedResources = relatedResourcesSelectorRegistry.getSelectors().stream()
          .flatMap(selector -> selector.getRelatedResources(parentResource).stream())
          .filter(relatedResource -> !parentResourcesPaths.contains(relatedResource.getPath()))
          .collect(Collectors.toCollection(LinkedHashSet::new));
      result.put(parentResource.getPath(), relatedResources);
    }
    return result;
  }

  private void publishRelatedResources(Set<ResourceInfo> relatedResources, Session session) throws JobCreationException {
    for (ResourceInfo relatedResource : relatedResources) {
      if (!PublishedRelatedResourcesManager.wasPublished(relatedResource, session)) {
        handlePublication(relatedResource, PublicationAction.PUBLISH);
      }
    }
  }

  private void unpublishRelatedResources(Map<String, Set<ResourceInfo>> relatedResourcesMap) throws JobCreationException {
    Set<ResourceInfo> relatedResourcesToUnpublish = new LinkedHashSet<>();
    for (Entry<String, Set<ResourceInfo>> relatedResourcesForParentPath : relatedResourcesMap.entrySet()) {
      String parentResourcePath = relatedResourcesForParentPath.getKey();
      for (ResourceInfo relatedResource : relatedResourcesForParentPath.getValue()) {
        if (InternalResourceDetector.isInternalResource(relatedResource.getPath(), parentResourcePath)) {
          relatedResourcesToUnpublish.add(relatedResource);
        }
      }
    }
    for (ResourceInfo relatedResource : relatedResourcesToUnpublish) {
      handlePublication(relatedResource, PublicationAction.UNPUBLISH);
    }
  }

  private void handlePublication(ResourceInfo resource, PublicationAction action)
      throws JobCreationException {
    LOG.trace("Handling publication for resource: {}", resource);
    String resourcePath = resource.getPath();
    for (PublicationHandler<?> handler : publicationHandlerRegistry.getForResource(resource)) {
      for (StreamxInstanceClient client : streamxClientStore.getForResource(resourcePath)) {
        LOG.debug("Adding publication request for [{}: {}] to queue", handler.getId(), resourcePath);
        addPublicationToQueue(handler.getId(), action, resource, client.getName());
      }
    }
  }

  private void addPublicationToQueue(String handlerId, PublicationAction action,
      ResourceInfo resource, String clientName) throws JobCreationException {
    String resourcePath = resource.getPath();

    Map<String, Object> jobProperties = new HashMap<>();
    jobProperties.put(PN_STREAMX_PUBLICATION_HANDLER_ID, handlerId);
    jobProperties.put(PN_STREAMX_PUBLICATION_CLIENT_NAME, clientName);
    jobProperties.put(PN_STREAMX_PUBLICATION_ACTION, action.toString());
    jobProperties.put(PN_STREAMX_PUBLICATION_PATH, resourcePath);
    jobProperties.put(PN_STREAMX_PUBLICATION_PROPERTIES, resource.getSerializedProperties());

    Job job = jobManager.addJob(PublicationJobExecutor.JOB_TOPIC, jobProperties);
    if (job == null) {
      throw new JobCreationException("Publication job could not be created by JobManager for " + resourcePath);
    }
    LOG.debug("Publication request for [{}: {}] added to queue. Job: {}", handlerId, resourcePath, job);
  }

  private static Session getSession(ResourceResolver resourceResolver) {
    return Objects.requireNonNull(resourceResolver.adaptTo(Session.class));
  }

  @ObjectClassDefinition(name = "StreamX Connector Configuration")
  @interface Config {

    @AttributeDefinition(name = "Enable publication to StreamX", description =
        "If the flag is unset the publication requests will be skipped.")
    boolean enabled() default true;
  }
}
