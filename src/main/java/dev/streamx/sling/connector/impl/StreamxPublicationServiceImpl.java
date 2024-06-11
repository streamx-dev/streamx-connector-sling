package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.exceptions.StreamxClientException;
import dev.streamx.clients.ingestion.publisher.Publisher;
import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.RelatedResource;
import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.StreamxPublicationService;
import dev.streamx.sling.connector.UnpublishData;
import dev.streamx.sling.connector.impl.StreamxPublicationServiceImpl.Config;
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
import org.apache.sling.event.jobs.consumer.JobConsumer;
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
    service = {StreamxPublicationService.class, JobConsumer.class},
    property = {JobConsumer.PROPERTY_TOPICS + "=" + StreamxPublicationServiceImpl.JOB_TOPIC}
)
@Designate(ocd = Config.class)
public class StreamxPublicationServiceImpl implements StreamxPublicationService, JobConsumer {

  static final String JOB_TOPIC = "dev/streamx/publications";

  private static final Logger LOG = LoggerFactory.getLogger(StreamxPublicationServiceImpl.class);

  private static final String PN_STREAMX_HANDLER_ID = "streamx.handler.id";
  private static final String PN_STREAMX_CLIENT_NAME = "streamx.client.name";
  private static final String PN_STREAMX_ACTION = "streamx.action";
  private static final String PN_STREAMX_PATH = "streamx.path";


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
    handlePublication(PublicationAction.PUBLISH, pathsToPublish);
  }

  @Override
  public void unpublish(List<String> pathsToUnpublish) throws StreamxPublicationException {
    handlePublication(PublicationAction.UNPUBLISH, pathsToUnpublish);
  }

  private void handlePublication(PublicationAction action, List<String> resourcesPaths)
      throws StreamxPublicationException {
    if (!enabled || resourcesPaths.isEmpty()) {
      return;
    }

    Set<RelatedResource> relatedResources = findRelatedResources(resourcesPaths, action);

    try {
      handleResourcesPublication(resourcesPaths, action);
      handleRelatedResourcesPublication(relatedResources);
    } catch (JobCreationException e) {
      throw new StreamxPublicationException("Can't handle publication. " + e.getMessage());
    }
  }

  private Set<RelatedResource> findRelatedResources(List<String> resourcesPaths,
      PublicationAction action)
      throws StreamxPublicationException {
    Set<RelatedResource> relatedResources = new LinkedHashSet<>();
    for (String resourcePath : resourcesPaths) {
      relatedResources.addAll(findRelatedResources(resourcePath, action));
    }

    Predicate<RelatedResource> shouldBePublished = shouldPublishResourcePredicate(resourcesPaths,
        action);
    return relatedResources.stream().filter(shouldBePublished)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private void handleResourcesPublication(List<String> resourcesPaths, PublicationAction action)
      throws JobCreationException {
    for (String resourcePath : resourcesPaths) {
      if (StringUtils.isBlank(resourcePath)) {
        continue;
      }

      handlePublication(resourcePath, action);
    }
  }

  private void handlePublication(String resourcePath, PublicationAction action)
      throws JobCreationException {
    for (PublicationHandler<?> handler : publicationHandlerRegistry.getHandlers()) {
      if (handler.canHandle(resourcePath)) {
        addPublicationToQueue(handler.getId(), action, resourcePath);
      }
    }
  }

  private void handleRelatedResourcesPublication(Set<RelatedResource> relatedResources)
      throws JobCreationException {
    for (RelatedResource relatedResource : relatedResources) {
      handlePublication(relatedResource.getResourcePath(), relatedResource.getAction());
    }
  }

  private Predicate<RelatedResource> shouldPublishResourcePredicate(List<String> publishedResources,
      PublicationAction action) {
    return relatedResource -> !isPublished(relatedResource, publishedResources, action);
  }

  private boolean isPublished(RelatedResource relatedResource, List<String> publishedResources,
      PublicationAction action) {
    return relatedResource.getAction().equals(action) && publishedResources.contains(
        relatedResource.getResourcePath());
  }

  private Set<RelatedResource> findRelatedResources(String resourcePath, PublicationAction action)
      throws StreamxPublicationException {
    Set<RelatedResource> relatedResources = new LinkedHashSet<>();
    for (RelatedResourcesSelector relatedResourcesSelector : relatedResourcesSelectorRegistry.getSelectors()) {
      relatedResources.addAll(relatedResourcesSelector.getRelatedResources(resourcePath, action));
    }
    return relatedResources;
  }

  private void addPublicationToQueue(String handlerId, PublicationAction action,
      String resourcePath) throws JobCreationException {
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
    jobProperties.put(PN_STREAMX_ACTION, action);
    jobProperties.put(PN_STREAMX_PATH, resourcePath);
    Job job = jobManager.addJob(JOB_TOPIC, jobProperties);
    if (job == null) {
      throw new JobCreationException("Publication job could not be created by JobManager");
    }
    LOG.debug("Publication request for [{}: {}] added to queue", handlerId, resourcePath);
  }

  @Override
  public JobResult process(Job job) {
    String handlerId = job.getProperty(PN_STREAMX_HANDLER_ID, String.class);
    String clientName = job.getProperty(PN_STREAMX_CLIENT_NAME, String.class);
    PublicationAction action = job.getProperty(PN_STREAMX_ACTION, PublicationAction.class);
    String path = job.getProperty(PN_STREAMX_PATH, String.class);
    if (StringUtils.isEmpty(path)) {
      LOG.warn("Publication job has no path");
      return JobResult.CANCEL;
    }

    LOG.debug("Processing job: [{} - {}]", action, path);
    try {
      return processPublication(handlerId, action, path, clientName);
    } catch (StreamxPublicationException | StreamxClientException e) {
      LOG.error("Error while processing publication, job will be retried", e);
      return JobResult.FAILED;
    } catch (RuntimeException e) {
      LOG.error("Unknown error while processing publication [{}, {}, {}]",
          handlerId, action, path, e);
      return JobResult.CANCEL;
    }
  }

  private JobResult processPublication(String handlerId, PublicationAction action, String path,
      String clientName)
      throws StreamxPublicationException, StreamxClientException {
    PublicationHandler<?> publicationHandler = findHandler(handlerId);
    if (publicationHandler == null) {
      LOG.warn("Cannot find publication handler with id: [{}]", handlerId);
      return JobResult.CANCEL;
    }
    StreamxInstanceClient streamxInstanceClient = streamxClientStore.getByName(clientName);
    if (streamxInstanceClient == null) {
      LOG.warn("Cannot find StreamX client with name: [{}]", clientName);
      return JobResult.CANCEL;
    }

    switch (action) {
      case PUBLISH:
        handlePublish(publicationHandler, streamxInstanceClient, path);
        break;
      case UNPUBLISH:
        handleUnpublish(publicationHandler, streamxInstanceClient, path);
        break;
      default:
        LOG.debug("Unsupported publication action: [{}]", action);
        return JobResult.CANCEL;
    }
    return JobResult.OK;
  }

  private PublicationHandler<?> findHandler(String handlerId) {
    return publicationHandlerRegistry.getHandlers().stream()
        .filter(handler -> handlerId.equals(handler.getId()))
        .findFirst()
        .orElse(null);
  }

  private void handlePublish(PublicationHandler<?> publicationHandler,
      StreamxInstanceClient streamxInstanceClient, String resourcePath)
      throws StreamxPublicationException, StreamxClientException {
    PublishData<?> publishData = publicationHandler.getPublishData(resourcePath);
    if (publishData == null) {
      LOG.debug("Publish data returned by [{}] is null", publicationHandler.getClass().getName());
      return;
    }
    handlePublish(publishData, streamxInstanceClient);
  }

  private <T> void handlePublish(PublishData<T> publishData,
      StreamxInstanceClient streamxInstanceClient)
      throws StreamxClientException {
    Publisher<T> publisher = streamxInstanceClient.getPublisher(publishData);
    publisher.publish(publishData.getKey(), publishData.getModel());
    LOG.info("Published resource: [{}] to [{}: {}]", publishData.getKey(),
        streamxInstanceClient.getName(), publishData.getChannel());
  }

  private void handleUnpublish(PublicationHandler<?> publicationHandler,
      StreamxInstanceClient streamxInstanceClient, String resourcePath)
      throws StreamxPublicationException, StreamxClientException {
    UnpublishData<?> unpublishData = publicationHandler.getUnpublishData(resourcePath);
    if (unpublishData == null) {
      LOG.debug("Unpublish data returned by [{}] is null",
          publicationHandler.getClass().getName());
      return;
    }
    handleUnpublish(unpublishData, streamxInstanceClient);
  }

  private <T> void handleUnpublish(UnpublishData<T> unpublishData,
      StreamxInstanceClient streamxInstanceClient)
      throws StreamxClientException {
    Publisher<T> publisher = streamxInstanceClient.getPublisher(unpublishData);
    publisher.unpublish(unpublishData.getKey());
    LOG.info("Unpublished resource: [{}] from [{}: {}]", unpublishData.getKey(),
        streamxInstanceClient.getName(), unpublishData.getChannel());
  }

  @ObjectClassDefinition(name = "StreamX Connector Configuration")
  @interface Config {

    @AttributeDefinition(name = "Enable publication to StreamX", description =
        "If the flag is unset the publication requests will be skipped.")
    boolean enabled() default true;
  }
}
