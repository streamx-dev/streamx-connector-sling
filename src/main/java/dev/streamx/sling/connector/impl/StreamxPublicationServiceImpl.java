package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.exceptions.StreamxClientException;
import dev.streamx.clients.ingestion.publisher.Publisher;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.StreamxPublicationService;
import dev.streamx.sling.connector.UnpublishData;
import dev.streamx.sling.connector.impl.StreamxPublicationServiceImpl.Config;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private static final String PN_STREAMX_ACTION = "streamx.action";
  private static final String PN_STREAMX_PATH = "streamx.path";

  private static final String ACTION_PUBLISH = "PUBLISH";
  private static final String ACTION_UNPUBLISH = "UNPUBLISH";

  @Reference
  private JobManager jobManager;

  @Reference
  private PublicationHandlerRegistry publicationHandlerRegistry;

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
    addPublicationToQueueIfCanHandle(ACTION_PUBLISH, pathsToPublish);
  }

  @Override
  public void unpublish(List<String> pathsToUnpublish) throws StreamxPublicationException {
    addPublicationToQueueIfCanHandle(ACTION_UNPUBLISH, pathsToUnpublish);
  }

  private void addPublicationToQueueIfCanHandle(String action, List<String> resourcesPaths)
      throws StreamxPublicationException {
    if (!enabled || resourcesPaths.isEmpty()) {
      return;
    }

    for (String resourcePath : resourcesPaths) {
      if (StringUtils.isBlank(resourcePath)) {
        continue;
      }
      for (PublicationHandler<?> handler : publicationHandlerRegistry.getHandlers()) {
        if (handler.canHandle(resourcePath)) {
          addPublicationToQueue(handler.getId(), action, resourcePath);
        }
      }
    }
  }

  private void addPublicationToQueue(String handlerId, String action, String resourcePath)
      throws StreamxPublicationException {
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
    try {
      return processPublication(handlerId, action, path);
    } catch (StreamxPublicationException | StreamxClientException e) {
      LOG.error("Error while processing publication, job will be retried", e);
      return JobResult.FAILED;
    } catch (RuntimeException e) {
      LOG.error("Unknown error while processing publication [{}, {}, {}]",
          handlerId, action, path, e);
      return JobResult.CANCEL;
    }
  }

  private JobResult processPublication(String handlerId, String action, String path)
      throws StreamxPublicationException, StreamxClientException {
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
    return publicationHandlerRegistry.getHandlers().stream()
        .filter(handler -> handlerId.equals(handler.getId()))
        .findFirst()
        .orElse(null);
  }

  private void handlePublish(PublicationHandler<?> publicationHandler, String resourcePath)
      throws StreamxPublicationException, StreamxClientException {
    PublishData<?> publishData = publicationHandler.getPublishData(resourcePath);
    if (publishData == null) {
      LOG.debug("Publish data returned by [{}] is null", publicationHandler.getClass().getName());
      return;
    }
    handlePublish(publishData, resourcePath);
  }

  private <T> void handlePublish(PublishData<T> publishData, String resourcePath)
      throws StreamxClientException {
    List<StreamxInstanceClient> clients = streamxClientStore.getForResource(resourcePath);
    for (StreamxInstanceClient client : clients) {
      Publisher<T> publisher = client.getPublisher(publishData);
      publisher.publish(publishData.getKey(), publishData.getModel());
      LOG.info("Published resource: [{}] to [{}]", publishData.getKey(), publishData.getChannel());
    }
  }

  private void handleUnpublish(PublicationHandler<?> publicationHandler, String resourcePath)
      throws StreamxPublicationException, StreamxClientException {
    UnpublishData<?> unpublishData = publicationHandler.getUnpublishData(resourcePath);
    if (unpublishData == null) {
      LOG.debug("Unpublish data returned by [{}] is null",
          publicationHandler.getClass().getName());
      return;
    }
    handleUnpublish(unpublishData, resourcePath);
  }

  private <T> void handleUnpublish(UnpublishData<T> unpublishData, String resourcePath)
      throws StreamxClientException {
    List<StreamxInstanceClient> clients = streamxClientStore.getForResource(resourcePath);
    for (StreamxInstanceClient client : clients) {
      Publisher<T> publisher = client.getPublisher(unpublishData);
      publisher.unpublish(unpublishData.getKey());
      LOG.info("Unpublished resource: [{}] to [{}]", unpublishData.getKey(),
          unpublishData.getChannel());
    }
  }

  @ObjectClassDefinition(name = "StreamX Connector Configuration")
  @interface Config {

    @AttributeDefinition(name = "Enable publication to StreamX", description =
        "If the flag is unset the publication requests will be skipped.")
    boolean enabled() default true;
  }
}
