package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_ACTION;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_CLIENT_NAME;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_HANDLER_ID;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_PATH;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_PROPERTIES;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.StreamxPublicationException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.JobManager.QueryType;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Submits publication jobs for resources
 */
@Component(
    service = JobExecutor.class,
    property = JobExecutor.PROPERTY_TOPICS + "=" + IngestionTriggerJobExecutor.JOB_TOPIC,
    immediate = true
)
public class IngestionTriggerJobExecutor implements JobExecutor {

  static final String JOB_TOPIC = "dev/streamx/ingestion-trigger";
  static final String PN_STREAMX_INGESTION_ACTION = "streamx.ingestion.action";
  static final String PN_STREAMX_INGESTION_RESOURCES = "streamx.ingestion.resources";

  private static final Logger LOG = LoggerFactory.getLogger(IngestionTriggerJobExecutor.class);

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

  /**
   * Constructs an instance of this class.
   */
  public IngestionTriggerJobExecutor() {
  }

  /**
   * Submits publication jobs for resources described by properties of the input job
   */
  @Override
  public JobExecutionResult process(Job job, JobExecutionContext jobExecutionContext) {
    LOG.trace("Processing {}", job);
    PublicationAction ingestionAction = extractPublicationAction(job);
    List<ResourceInfo> resources = extractResourcesInfo(job);
    LOG.trace("Submitting publication jobs for paths: {}", resources);

    if (!resources.isEmpty()) {
      try (ResourceResolver resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver( null)) {
        submitResourcesPublicationJob(ingestionAction, resources);
        submitRelatedResourcesPublicationJob(ingestionAction, resources, resourceResolver);
      } catch (StreamxPublicationException exception) {
        LOG.error("Error while processing job", exception);
        return jobExecutionContext.result() .message("Error while processing job: " + exception.getMessage()).failed();
      } catch (LoginException | RepositoryException ex) {
        LOG.error("Error updating JCR state for related resources", ex);
      }
    }
    return jobExecutionContext.result().succeeded();
  }

  private void submitResourcesPublicationJob(PublicationAction action, List<ResourceInfo> resources) throws StreamxPublicationException {
    try {
      for (ResourceInfo resource : resources) {
        submitPublicationJob(resource, action);
      }
    } catch (Exception e) {
      throw new StreamxPublicationException("Can't submit publication jobs for resources. " + e.getMessage(), e);
    }
  }

  private void submitRelatedResourcesPublicationJob(PublicationAction action, List<ResourceInfo> resources, ResourceResolver resourceResolver)
      throws RepositoryException, StreamxPublicationException {
    try {
      Map<String, Set<ResourceInfo>> relatedResourcesMap = findRelatedResources(resources);
      Session session = Objects.requireNonNull(resourceResolver.adaptTo(Session.class));
      if (action == PublicationAction.PUBLISH) {
        Set<ResourceInfo> distinctRelatedResources = SetUtils.flattenToLinkedHashSet(relatedResourcesMap.values());
        submitPublishRelatedResourcesJob(distinctRelatedResources, session);
        Map<String, Set<ResourceInfo>> disappearedRelatedResources = PublishedRelatedResourcesManager.updatePublishedResourcesData(relatedResourcesMap, session);
        submitUnpublishRelatedResourcesJob(disappearedRelatedResources);
      } else if (action == PublicationAction.UNPUBLISH) {
        submitUnpublishRelatedResourcesJob(relatedResourcesMap);
        PublishedRelatedResourcesManager.removePublishedResourcesData(resources, session);
      }
      if (session.hasPendingChanges()) {
        session.save();
      }
    } catch (RepositoryException ex) {
      throw ex;
    } catch (Exception e) {
      throw new StreamxPublicationException("Can't submit publication jobs for related resources. " + e.getMessage(), e);
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

  private void submitPublishRelatedResourcesJob(Set<ResourceInfo> relatedResources, Session session) throws JobCreationException {
    for (ResourceInfo relatedResource : relatedResources) {
      if (!PublishedRelatedResourcesManager.wasPublished(relatedResource, session)) {
        submitPublicationJob(relatedResource, PublicationAction.PUBLISH);
      }
    }
  }

  private void submitUnpublishRelatedResourcesJob(Map<String, Set<ResourceInfo>> relatedResourcesMap) throws JobCreationException {
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
      submitPublicationJob(relatedResource, PublicationAction.UNPUBLISH);
    }
  }

  private void submitPublicationJob(ResourceInfo resource, PublicationAction action) throws JobCreationException {
    String resourcePath = resource.getPath();
    if (isPublicationJobAlreadySubmitted(resourcePath, action)) {
      LOG.warn("Publication job for resource {} is already submitted", resource);
      return;
    }

    LOG.trace("Submitting publication job for resource {}", resource);
    for (PublicationHandler<?> handler : publicationHandlerRegistry.getForResource(resource)) {
      for (StreamxInstanceClient client : streamxClientStore.getForResource(resource)) {
        LOG.debug("Adding publication request for [{}: {}] to queue", handler.getId(), resourcePath);
        submitPublicationJob(handler.getId(), action, resource, client.getName());
      }
    }
  }

  private void submitPublicationJob(String handlerId, PublicationAction action,
      ResourceInfo resource, String clientName) throws JobCreationException {
    String resourcePath = resource.getPath();

    Map<String, Object> jobProperties = Map.of(
        PN_STREAMX_PUBLICATION_HANDLER_ID, handlerId,
        PN_STREAMX_PUBLICATION_CLIENT_NAME, clientName,
        PN_STREAMX_PUBLICATION_ACTION, action.toString(),
        PN_STREAMX_PUBLICATION_PATH, resourcePath,
        PN_STREAMX_PUBLICATION_PROPERTIES, resource.getSerializedProperties()
    );

    Job job = jobManager.addJob(PublicationJobExecutor.JOB_TOPIC, jobProperties);
    if (job == null) {
      throw new JobCreationException("Publication job could not be created by JobManager for " + resourcePath);
    }
    LOG.debug("Publication request for [{}: {}] added to queue. Job: {}", handlerId, resourcePath, job);
  }

  private boolean isPublicationJobAlreadySubmitted(String resourcePath, PublicationAction action) {
    return isPublicationJobAlreadySubmitted(QueryType.ACTIVE, resourcePath, action)
           ||
           isPublicationJobAlreadySubmitted(QueryType.QUEUED, resourcePath, action);
  }

  private boolean isPublicationJobAlreadySubmitted(QueryType queryType, String resourcePath, PublicationAction action) {
    Map<String, Object> propertiesForSearch = Map.of(
        PN_STREAMX_PUBLICATION_ACTION, action.toString(),
        PN_STREAMX_PUBLICATION_PATH, resourcePath
    );
    int limit = 1;
    return !jobManager.findJobs(queryType, PublicationJobExecutor.JOB_TOPIC, limit, propertiesForSearch).isEmpty();
  }

  static PublicationAction extractPublicationAction(Job job) {
    String publicationActionRaw = job.getProperty(PN_STREAMX_INGESTION_ACTION, String.class);
    return PublicationAction.of(publicationActionRaw).orElseThrow();
  }

  static List<ResourceInfo> extractResourcesInfo(Job job) {
    String[] resourcesInfoRaw = job.getProperty(PN_STREAMX_INGESTION_RESOURCES, String[].class);
    return Stream.of(resourcesInfoRaw)
        .map(ResourceInfo::deserialize)
        .filter(resource -> resource.getPath() != null)
        .collect(Collectors.toUnmodifiableList());
  }
}
