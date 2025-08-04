package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.ResourceInfo;
import java.util.Collection;
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

  private static final int FIND_JOBS_LIMIT = 1;
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
    LOG.trace("Processing ingestion trigger job {}", job);
    PublicationAction action;
    List<ResourceInfo> resources;

    try {
      action = extractPublicationAction(job);
      resources = extractResourcesInfo(job);
    } catch (Exception ex) {
      LOG.error("Error reading data from job properties", ex);
      return jobExecutionContext.result().message(ex.getMessage()).cancelled();
    }

    if (!resources.isEmpty()) {
      try (ResourceResolver resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver( null)) {
        submitPublicationJobsForMainResources(action, resources);
        submitPublicationJobsForRelatedResources(action, resources, resourceResolver);
      } catch (PublicationJobSubmitException exception) {
        LOG.error("Error while submitting {} job", action, exception);
        return jobExecutionContext.result().message("Error while processing job: " + exception.getMessage()).failed();
      } catch (LoginException | RepositoryException ex) {
        LOG.error("Error updating JCR state for related resources", ex);
      }
    }
    return jobExecutionContext.result().succeeded();
  }

  private void submitPublicationJobsForMainResources(PublicationAction action, List<ResourceInfo> resources) throws PublicationJobSubmitException {
    try {
      for (ResourceInfo resource : resources) {
        submitPublicationJobs(resource, action);
      }
    } catch (Exception e) {
      throw new PublicationJobSubmitException("Can't submit " + action + " jobs for resources. " + e.getMessage(), e);
    }
  }

  private void submitPublicationJobsForRelatedResources(PublicationAction action, List<ResourceInfo> resources, ResourceResolver resourceResolver)
      throws RepositoryException, PublicationJobSubmitException {
    List<RelatedResourcesSelector> relatedResourcesSelectors = relatedResourcesSelectorRegistry.getSelectors();
    if (relatedResourcesSelectors.isEmpty()) {
      return;
    }

    try {
      Map<String, Set<ResourceInfo>> relatedResourcesMap = findRelatedResources(resources, relatedResourcesSelectors);
      Session session = Objects.requireNonNull(resourceResolver.adaptTo(Session.class));
      if (action == PublicationAction.PUBLISH) {
        Set<ResourceInfo> distinctRelatedResources = SetUtils.flattenToLinkedHashSet(relatedResourcesMap.values());
        submitPublishJobsForRelatedResources(distinctRelatedResources, session);
        Map<String, Set<ResourceInfo>> disappearedRelatedResources = PublishedRelatedResourcesTreeManager.updatePublishedResourcesData(relatedResourcesMap, session);
        if (!disappearedRelatedResources.isEmpty()) {
          LOG.trace("Detected the following disappeared related resources for parent resources: {}", disappearedRelatedResources);
          submitUnpublishJobsForRelatedResources(disappearedRelatedResources);
        }
      } else if (action == PublicationAction.UNPUBLISH) {
        submitUnpublishJobsForRelatedResources(relatedResourcesMap);
        PublishedRelatedResourcesTreeManager.removePublishedResourcesData(resources, session);
      }
      if (session.hasPendingChanges()) {
        session.save();
      }
    } catch (RepositoryException ex) {
      throw ex;
    } catch (Exception e) {
      throw new PublicationJobSubmitException("Can't submit " + action + " jobs for related resources. " + e.getMessage(), e);
    }
  }

  private Map<String, Set<ResourceInfo>> findRelatedResources(List<ResourceInfo> parentResources, List<RelatedResourcesSelector> relatedResourcesSelectors) {
    LOG.trace("Searching for related resources of parent resources {}", parentResources);
    Set<String> parentResourcesPaths = SetUtils.mapToLinkedHashSet(parentResources, ResourceInfo::getPath);

    Map<String, Set<ResourceInfo>> result = new LinkedHashMap<>();
    for (ResourceInfo parentResource : parentResources) {
      Set<ResourceInfo> relatedResources = new LinkedHashSet<>();
      for (RelatedResourcesSelector selector : relatedResourcesSelectors) {
        relatedResources.addAll(selector.getRelatedResources(parentResource));
      }
      relatedResources.removeIf(relatedResource -> parentResourcesPaths.contains(relatedResource.getPath()));
      result.put(parentResource.getPath(), relatedResources);
    }
    return result;
  }

  private void submitPublishJobsForRelatedResources(Set<ResourceInfo> relatedResources, Session session) throws JobCreationException {
    final PublicationAction action = PublicationAction.PUBLISH;

    for (ResourceInfo relatedResource : relatedResources) {
      if (PublishedRelatedResourcesTreeManager.wasPublished(relatedResource, session)) {
        LOG.trace("Skipping submitting {} jobs for related resource {} because it is marked as already published", action, relatedResource);
      } else {
        submitPublicationJobs(relatedResource, action);
      }
    }
  }

  private void submitUnpublishJobsForRelatedResources(Map<String, Set<ResourceInfo>> relatedResourcesMap) throws JobCreationException {
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
      submitPublicationJobs(relatedResource, PublicationAction.UNPUBLISH);
    }
  }

  private void submitPublicationJobs(ResourceInfo resource, PublicationAction action) throws JobCreationException {
    LOG.trace("Attempting to submit {} jobs for resource {} for matching handlers and clients", action, resource);
    for (PublicationHandler<?> handler : publicationHandlerRegistry.getForResource(resource)) {
      for (StreamxInstanceClient client : streamxClientStore.getForResource(resource)) {
        submitPublicationJob(handler.getId(), action, resource, client.getName());
      }
    }
  }

  private void submitPublicationJob(String handlerId, PublicationAction action,
      ResourceInfo resource, String clientName) throws JobCreationException {
    LOG.trace("Submitting {} job for resource {}, handler '{}' and client '{}'", action, resource, handlerId, clientName);

    Map<String, Object> jobProperties = new PublicationJobProperties()
        .withHandlerId(handlerId)
        .withClientName(clientName)
        .withAction(action)
        .withResource(resource)
        .asMap();

    if (isPublicationJobAlreadySubmitted(jobProperties)) {
      LOG.info("{} job for resource {} with job properties {} is already submitted", action, resource, jobProperties);
      return;
    }

    Job job = jobManager.addJob(PublicationJobExecutor.JOB_TOPIC, jobProperties);
    if (job == null) {
      throw new JobCreationException(action + " job could not be created by JobManager for " + resource);
    }
  }

  private boolean isPublicationJobAlreadySubmitted(Map<String, Object> jobProperties) {
    return isPublicationJobAlreadySubmitted(QueryType.ACTIVE, jobProperties)
           ||
           isPublicationJobAlreadySubmitted(QueryType.QUEUED, jobProperties);
  }

  private boolean isPublicationJobAlreadySubmitted(QueryType queryType, Map<String, Object> jobProperties) {
    @SuppressWarnings("unchecked")
    Collection<Job> foundJobs = jobManager.findJobs(queryType, PublicationJobExecutor.JOB_TOPIC, FIND_JOBS_LIMIT, jobProperties);
    return !foundJobs.isEmpty();
  }

  static PublicationAction extractPublicationAction(Job job) {
    String publicationActionRaw = IngestionTriggerJobProperties.getAction(job);
    return PublicationAction.of(publicationActionRaw).orElseThrow();
  }

  static List<ResourceInfo> extractResourcesInfo(Job job) {
    String[] resourcesInfoRaw = IngestionTriggerJobProperties.getResources(job);
    return Stream.of(resourcesInfoRaw)
        .map(ResourceInfo::deserialize)
        .filter(resource -> resource.getPath() != null)
        .collect(Collectors.toUnmodifiableList());
  }
}
