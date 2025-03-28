package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_ACTION;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_CLIENT_NAME;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_HANDLER_ID;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PATH;

import dev.streamx.sling.connector.IngestedData;
import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.RelatedResource;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.StreamxPublicationService;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    service = {StreamxPublicationService.class, JobExecutor.class},
    property = JobExecutor.PROPERTY_TOPICS + "=" + JobAsIngestedData.JOB_TOPIC,
    immediate = true
)
@Designate(ocd = StreamxPublicationServiceImplConfig.class)
public class StreamxPublicationServiceImpl implements StreamxPublicationService, JobExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(StreamxPublicationServiceImpl.class);

  private final JobManager jobManager;
  private final PublicationHandlerRegistry publicationHandlerRegistry;
  private final RelatedResourcesSelectorRegistry relatedResourcesSelectorRegistry;
  private final StreamxClientStore streamxClientStore;
  private final ResourceResolverFactory resourceResolverFactory;
  private final AtomicReference<StreamxPublicationServiceImplConfig> config;

  @Activate
  @SuppressWarnings("ConstructorWithTooManyParameters")
  public StreamxPublicationServiceImpl(
      @Reference(cardinality = ReferenceCardinality.MANDATORY)
      JobManager jobManager,
      @Reference(cardinality = ReferenceCardinality.MANDATORY)
      PublicationHandlerRegistry publicationHandlerRegistry,
      @Reference(cardinality = ReferenceCardinality.MANDATORY)
      RelatedResourcesSelectorRegistry relatedResourcesSelectorRegistry,
      @Reference(cardinality = ReferenceCardinality.MANDATORY)
      StreamxClientStore streamxClientStore,
      @Reference(cardinality = ReferenceCardinality.MANDATORY)
      ResourceResolverFactory resourceResolverFactory,
      StreamxPublicationServiceImplConfig config
  ) {
    this.jobManager = jobManager;
    this.publicationHandlerRegistry = publicationHandlerRegistry;
    this.relatedResourcesSelectorRegistry = relatedResourcesSelectorRegistry;
    this.streamxClientStore = streamxClientStore;
    this.resourceResolverFactory = resourceResolverFactory;
    this.config = new AtomicReference<>(config);
  }

  @Modified
  void configure(StreamxPublicationServiceImplConfig config) {
    this.config.set(config);
  }

  @Override
  public boolean isEnabled() {
    return config.get().enabled();
  }

  @Override
  public void ingest(IngestedData ingestedData) {
    Map<String, Object> jobProps = new JobAsIngestedData(ingestedData).asJobProps();
    LOG.trace("Adding job with these properties: {}", jobProps);
    Job addedJob = jobManager.addJob(JobAsIngestedData.JOB_TOPIC, jobProps);
    LOG.debug("Added job: {}", addedJob);
  }

  @Override
  public JobExecutionResult process(Job job, JobExecutionContext jobExecutionContext) {
    LOG.trace("Processing {}", job);
    try {
      executeIngestion(new JobAsIngestedData(job, resourceResolverFactory));
      return jobExecutionContext.result().succeeded();
    } catch (StreamxPublicationException exception) {
      String message = String.format("Unable to process %s", job);
      LOG.error(message, exception);
      return jobExecutionContext.result().failed();
    }
  }

  @SuppressWarnings("squid:S1130")
  private void executeIngestion(IngestedData ingestedData) throws StreamxPublicationException {
    LOG.trace("Executing ingestion of {}", ingestedData);
    if (!config.get().enabled()) {
      LOG.trace("{} is disabled. Skipping ingestion of {}", this, ingestedData);
      return;
    }
    boolean isPublish = ingestedData.ingestionAction() == PublicationAction.PUBLISH;
    Collection<IngestedData> relatedResources =
        isPublish ? findRelatedResources(ingestedData) : Set.of();
    List<IngestedData> allIngestedData = Stream.concat(
        Stream.of(ingestedData), relatedResources.stream()
    ).collect(Collectors.toUnmodifiableList());
    LOG.trace("Resolved these data to ingest: {}", allIngestedData);
    publicationHandlerRegistry.getHandlers().forEach(
        handler -> allIngestedData.stream()
            .filter(handler::canHandle)
            .forEach(
                filteredIngestedData -> submitIngestionJob(
                    handler.getId(), filteredIngestedData
                )
            )
    );
  }

  private Set<IngestedData> findRelatedResources(IngestedData ingestedData) {
    SlingUri slingUri = ingestedData.uriToIngest();
    PublicationAction ingestionAction = ingestedData.ingestionAction();
    Set<IngestedData> relatedResources = relatedResourcesSelectorRegistry.getSelectors()
        .stream()
        .flatMap(
            selector -> {
              try {
                return selector.getRelatedResources(slingUri.toString(), ingestionAction).stream();
              } catch (StreamxPublicationException exception) {
                String message = String.format(
                    "Unable to get related resources for %s and %s with %s",
                    slingUri, ingestionAction, selector
                );
                LOG.error(message, exception);
                return Stream.empty();
              }
            }
        ).filter(shouldPublishResourcePredicate(ingestedData))
        .map(
            relatedResource -> new RelatedResourceAsIngestedData(
                relatedResource, resourceResolverFactory
            )
        ).collect(Collectors.toUnmodifiableSet());
    LOG.debug("For {} found these related resources: {}", slingUri, relatedResources);
    return relatedResources;
  }

  private Predicate<RelatedResource> shouldPublishResourcePredicate(IngestedData ingestedData) {
    return relatedResource -> !isPublished(relatedResource, ingestedData);
  }

  private boolean isPublished(
      RelatedResource relatedResource, IngestedData ingestedData
  ) {
    String relatedResourcePath = relatedResource.getResourcePath();
    String ingestedResourcePath = Optional.ofNullable(ingestedData.uriToIngest().getResourcePath())
        .orElse(StringUtils.EMPTY);
    return relatedResource.getAction() == ingestedData.ingestionAction()
        && ingestedResourcePath.equals(relatedResourcePath);
  }

  private void submitIngestionJob(String handlerId, IngestedData ingestedData) {
    String resourcePath = ingestedData.uriToIngest().toString();
    streamxClientStore.getForResource(resourcePath).stream()
        .map(StreamxInstanceClient::getName)
        .forEach(clientName -> submitIngestionJob(handlerId, ingestedData, clientName));
  }

  private void submitIngestionJob(String handlerId, IngestedData ingestedData, String clientName) {
    SlingUri slingUri = ingestedData.uriToIngest();
    LOG.trace("Submitting ingestion job for [{}: {} | {}]", handlerId, slingUri, clientName);
    Map<String, Object> jobProperties = new HashMap<>();
    jobProperties.put(PN_STREAMX_HANDLER_ID, handlerId);
    jobProperties.put(PN_STREAMX_CLIENT_NAME, clientName);
    jobProperties.put(PN_STREAMX_ACTION, ingestedData.ingestionAction().toString());
    jobProperties.put(PN_STREAMX_PATH, slingUri.toString());
    Optional.ofNullable(
        jobManager.addJob(PublicationJobExecutor.JOB_TOPIC, jobProperties)
    ).ifPresentOrElse(
        job -> LOG.debug(
            "Publication request for [{}: {}] added to queue. Job: {}", handlerId, slingUri, job
        ),
        () -> {
          throw new JobCreationException(
              String.format("Unable to submit a job for %s", ingestedData)
          );
        }
    );
  }

}
