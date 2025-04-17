package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_ACTION;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_CLIENT_NAME;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_HANDLER_ID;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PATH;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.RelatedResource;
import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.StreamxPublicationService;
import dev.streamx.sling.connector.impl.StreamxPublicationServiceImpl.Config;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.api.uri.SlingUriBuilder;
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
    property = JobExecutor.PROPERTY_TOPICS + "=" + IngestionTrigger.JOB_TOPIC,
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
  public void publish(List<String> pathsToPublish) throws StreamxPublicationException {
    submitIngestionTriggerJob(PublicationAction.PUBLISH, pathsToPublish);
  }

  @Override
  public void unpublish(List<String> pathsToUnpublish) throws StreamxPublicationException {
    submitIngestionTriggerJob(PublicationAction.UNPUBLISH, pathsToUnpublish);
  }

  private void submitIngestionTriggerJob(
      PublicationAction ingestionAction, Collection<String> pathsToIngest
  ) {
    List<SlingUri> slingUris = pathsToIngest.stream()
        .map(this::toSlingUri)
        .flatMap(Optional::stream)
        .collect(Collectors.toUnmodifiableList());

    IngestionTrigger ingestionTrigger = new IngestionTrigger(ingestionAction, slingUris);
    Map<String, Object> jobProps = ingestionTrigger.asJobProps();
    Job addedJob = jobManager.addJob(IngestionTrigger.JOB_TOPIC, jobProps);
    LOG.debug("Added job: {}", addedJob);
  }

  @SuppressWarnings({"squid:S1874", "deprecation"})
  private Optional<SlingUri> toSlingUri(String rawUri) {
    try (
        ResourceResolver resourceResolver
            = resourceResolverFactory.getAdministrativeResourceResolver(null)
    ) {
      SlingUri slingUri = SlingUriBuilder.parse(rawUri, resourceResolver).build();
      LOG.trace("Parsed URI: {}", slingUri);
      return Optional.of(slingUri);
    } catch (LoginException exception) {
      String message = String.format("Unable to parse URI: '%s'", rawUri);
      LOG.error(message, exception);
      return Optional.empty();
    }
  }

  private void handlePublication(PublicationAction action, List<String> resourcesPaths)
      throws StreamxPublicationException {
    LOG.trace("Handling publication for paths: {}", resourcesPaths);
    if (!enabled || resourcesPaths.isEmpty()) {
      return;
    }

    boolean isPublish = action == PublicationAction.PUBLISH;
    Set<RelatedResource> relatedResources =
        isPublish ? findRelatedResources(resourcesPaths, action) : Set.of();

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
    LOG.trace("Searching for related resources for {} and these paths: {}", action, resourcesPaths);
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
    LOG.trace("Handling publication for resource: {}", resourcePath);
    for (PublicationHandler<?> handler : publicationHandlerRegistry.getHandlers()) {
      if (handler.canHandle(resourcePath)) {
        addPublicationToQueue(handler.getId(), action, resourcePath);
      }
    }
  }

  private void handleRelatedResourcesPublication(Set<RelatedResource> relatedResources)
      throws JobCreationException {
    for (RelatedResource relatedResource : relatedResources) {
      LOG.trace("Handling related resource publication: {}", relatedResource);
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

  @Override
  public JobExecutionResult process(Job job, JobExecutionContext jobExecutionContext) {
    LOG.trace("Processing {}", job);
    IngestionTrigger ingestionTrigger = new IngestionTrigger(job, resourceResolverFactory);
    PublicationAction ingestionAction = ingestionTrigger.ingestionAction();
    List<String> slingUrisRaw = ingestionTrigger.urisToIngest().stream().map(SlingUri::toString)
        .collect(Collectors.toUnmodifiableList());
    try {
      handlePublication(ingestionAction, slingUrisRaw);
      return jobExecutionContext.result().succeeded();
    } catch (StreamxPublicationException exception) {
      return jobExecutionContext.result().failed();
    }
  }

  @ObjectClassDefinition(name = "StreamX Connector Configuration")
  @interface Config {

    @AttributeDefinition(name = "Enable publication to StreamX", description =
        "If the flag is unset the publication requests will be skipped.")
    boolean enabled() default true;
  }
}
