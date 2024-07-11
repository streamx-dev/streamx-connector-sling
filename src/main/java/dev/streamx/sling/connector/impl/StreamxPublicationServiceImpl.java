package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_ACTION;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_CLIENT_NAME;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_HANDLER_ID;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PATH;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.JOB_TOPIC;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.RelatedResource;
import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.StreamxPublicationService;
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
    service = StreamxPublicationService.class
)
@Designate(ocd = Config.class)
public class StreamxPublicationServiceImpl implements StreamxPublicationService {

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

  @ObjectClassDefinition(name = "StreamX Connector Configuration")
  @interface Config {

    @AttributeDefinition(name = "Enable publication to StreamX", description =
        "If the flag is unset the publication requests will be skipped.")
    boolean enabled() default true;
  }
}
