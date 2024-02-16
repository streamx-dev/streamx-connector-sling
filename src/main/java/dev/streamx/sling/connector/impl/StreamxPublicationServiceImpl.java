package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.StreamxClient;
import dev.streamx.clients.ingestion.exceptions.StreamxClientException;
import dev.streamx.clients.ingestion.publisher.Publisher;
import dev.streamx.sling.connector.PublicationData;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.StreamxPublicationService;
import dev.streamx.sling.connector.UnpublishData;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    service = {StreamxPublicationService.class, JobConsumer.class},
    property = {JobConsumer.PROPERTY_TOPICS + "=" + StreamxPublicationServiceImpl.JOB_TOPIC}
)
public class StreamxPublicationServiceImpl implements StreamxPublicationService, JobConsumer {

  static final String JOB_TOPIC = "dev/streamx/sling/connector";

  private static final Logger LOG = LoggerFactory.getLogger(StreamxPublicationServiceImpl.class);

  private static final String PN_STREAMX_HANDLER_ID = "streamx.handler.id";
  private static final String PN_STREAMX_ACTION = "streamx.action";
  private static final String PN_STREAMX_PATH = "streamx.path";

  private static final String ACTION_PUBLISH = "PUBLISH";
  private static final String ACTION_UNPUBLISH = "UNPUBLISH";

  @Reference
  private ResourceResolverFactory resolverFactory;

  @Reference
  private JobManager jobManager;

  @Reference(
      service = PublicationHandler.class,
      cardinality = ReferenceCardinality.AT_LEAST_ONE,
      policyOption = ReferencePolicyOption.GREEDY)
  private List<PublicationHandler<?>> publicationHandlers;

  @Reference
  private StreamxClientFactory streamxClientFactory;

  private boolean enabled;
  private StreamxClient streamxClient;
  private Map<String, Publisher<?>> publishers;

  private static String getKey(PublicationData<?> publication) throws UnsupportedEncodingException {
    return URLEncoder.encode(publication.getKey(), StandardCharsets.UTF_8.toString());
  }

  @Activate
  @Modified
  private void activate(Config config) throws StreamxClientException {
    enabled = config.enabled();
    streamxClient = streamxClientFactory.createStreamxClient(config.streamxUrl());
    publishers = new HashMap<>();
  }

  @Deactivate
  private void deactivate() throws StreamxClientException {
    if (streamxClient != null) {
      streamxClient.close();
    }
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void publish(List<String> pathsToPublish) {
    addPublicationToQueueIfCanHandle(ACTION_PUBLISH, pathsToPublish);
  }

  @Override
  public void unpublish(List<String> pathsToUnpublish) {
    addPublicationToQueueIfCanHandle(ACTION_UNPUBLISH, pathsToUnpublish);
  }

  private void addPublicationToQueueIfCanHandle(String action, List<String> resourcesPaths) {
    if (!enabled || resourcesPaths.isEmpty()) {
      return;
    }

    for (String resourcePath : resourcesPaths) {
      if (StringUtils.isBlank(resourcePath)) {
        continue;
      }
      for (PublicationHandler<?> handler : publicationHandlers) {
        if (handler.canHandle(resourcePath)) {
          addPublicationToQueue(handler.getId(), action, resourcePath);
        }
      }
    }
  }

  private void addPublicationToQueue(String handlerId, String action, String resourcePath) {
    Map<String, Object> jobProperties = new HashMap<>();
    jobProperties.put(PN_STREAMX_HANDLER_ID, handlerId);
    jobProperties.put(PN_STREAMX_ACTION, action);
    jobProperties.put(PN_STREAMX_PATH, resourcePath);
    Job job = jobManager.addJob(JOB_TOPIC, jobProperties);
    if (job == null) {
      throw new StreamxPublicationException("Publication job could not be created by JobManager");
    }
    LOG.debug("Publication request for [{}: {}] added to queue", handlerId, resourcePath);
  }

  @Override
  public JobResult process(Job job) {
    String handlerId = job.getProperty(PN_STREAMX_HANDLER_ID, String.class);
    String action = job.getProperty(PN_STREAMX_ACTION, String.class);
    String path = job.getProperty(PN_STREAMX_PATH, String.class);
    if (StringUtils.isEmpty(path)) {
      LOG.warn("Publication job has no path");
      return JobResult.CANCEL;
    }

    LOG.debug("Processing job: [{} - {}]", action, path);
    return processPublication(handlerId, action, path);
  }

  private JobResult processPublication(String handlerId, String action, String path) {
    PublicationHandler<?> publicationHandler = findHandler(handlerId);
    if (publicationHandler == null) {
      LOG.warn("Cannot find publication handler with id: [{}]", handlerId);
      return JobResult.CANCEL;
    }

    switch (action) {
      case ACTION_PUBLISH:
        handlePublish(publicationHandler, path);
        break;
      case ACTION_UNPUBLISH:
        handleUnpublish(publicationHandler, path);
        break;
      default:
        LOG.debug("Unsupported publication action: [{}]", action);
        return JobResult.CANCEL;
    }
    return JobResult.OK;
  }

  private PublicationHandler<?> findHandler(String handlerId) {
    return publicationHandlers.stream()
        .filter(handler -> handlerId.equals(handler.getId()))
        .findFirst()
        .orElse(null);
  }

  private void handlePublish(PublicationHandler<?> publicationHandler, String resourcePath) {
    try (ResourceResolver resolver = createResourceResolver()) {
      Resource resource = resolver.getResource(resourcePath);
      if (resource == null) {
        LOG.error("Cannot get resource: [{}]", resourcePath);
        return;
      }
      PublishData<?> publishData = publicationHandler.getPublishData(resource);
      if (publishData == null) {
        LOG.debug("Publish data returned by [{}] is null", publicationHandler.getClass().getName());
        return;
      }
      handlePublish(publishData);
    } catch (StreamxClientException | LoginException | UnsupportedEncodingException e) {
      LOG.error("Cannot publish to StreamX", e);
    }
  }

  private ResourceResolver createResourceResolver() throws LoginException {
    return resolverFactory.getAdministrativeResourceResolver(null);
  }

  private <T> void handlePublish(PublishData<T> publishData)
      throws StreamxClientException, UnsupportedEncodingException {
    Publisher<T> publisher = getPublisher(publishData);
    publisher.publish(getKey(publishData), publishData.getModel());
    LOG.info("Published resource: [{}]", publishData.getKey());
  }

  private void handleUnpublish(PublicationHandler<?> publicationHandler, String resourcePath) {
    try {
      UnpublishData<?> unpublishData = publicationHandler.getUnpublishData(resourcePath);
      if (unpublishData == null) {
        LOG.debug("Unpublish data returned by [{}] is null",
            publicationHandler.getClass().getName());
        return;
      }
      handleUnpublish(unpublishData);
    } catch (StreamxClientException | UnsupportedEncodingException e) {
      LOG.error("Cannot unpublish from StreamX", e);
    }
  }

  private <T> void handleUnpublish(UnpublishData<T> unpublishData)
      throws StreamxClientException, UnsupportedEncodingException {
    Publisher<T> publisher = getPublisher(unpublishData);
    publisher.unpublish(getKey(unpublishData));
    LOG.info("Unpublished resource: [{}]", unpublishData.getKey());
  }

  private <T> Publisher<T> getPublisher(PublicationData<T> publication) {
    return (Publisher<T>) publishers.computeIfAbsent(
        publication.getChannel(),
        channel -> streamxClient.newPublisher(channel, publication.getModelClass()));
  }

  @ObjectClassDefinition(name = "StreamX Connector Configuration")
  @interface Config {

    @AttributeDefinition(name = "Enable publication to StreamX", description =
        "If the flag is unset the publication requests will be skipped.")
    boolean enabled() default true;

    @AttributeDefinition(name = "URL to StreamX", description =
        "URL to StreamX instance that will receive publication requests.")
    String streamxUrl();
  }
}
