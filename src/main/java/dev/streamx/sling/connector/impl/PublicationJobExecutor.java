package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.PublicationJobExecutor.JOB_TOPIC;

import dev.streamx.clients.ingestion.exceptions.StreamxClientException;
import dev.streamx.clients.ingestion.publisher.Message;
import dev.streamx.clients.ingestion.publisher.Publisher;
import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublicationRetryPolicy;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.UnpublishData;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes the ingestion process into StreamX.
 */
@Component(
    service = JobExecutor.class,
    property = {JobExecutor.PROPERTY_TOPICS + "=" + JOB_TOPIC}
)
public class PublicationJobExecutor implements JobExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(PublicationJobExecutor.class);

  static final String JOB_TOPIC = "dev/streamx/publications";

  @Reference
  private StreamxClientStore streamxClientStore;

  @Reference
  private PublicationHandlerRegistry publicationHandlerRegistry;

  @Reference
  private PublicationRetryPolicy publicationRetryPolicy;

  /**
   * Constructs an instance of this class.
   */
  public PublicationJobExecutor() {
  }

  @Override
  public JobExecutionResult process(Job job, JobExecutionContext context) {
    LOG.trace("Processing {}", job);
    String handlerId = PublicationJobProperties.getHandlerId(job);
    String clientName = PublicationJobProperties.getClientName(job);
    Optional<PublicationAction> actionNullable = PublicationAction.of(PublicationJobProperties.getAction(job));
    if (actionNullable.isEmpty()) {
      LOG.warn("Publication action is not set, job will be cancelled: {}", job);
      return context.result().cancelled();
    }
    PublicationAction action = actionNullable.orElseThrow();
    String path = PublicationJobProperties.getResourcePath(job);
    if (StringUtils.isEmpty(path)) {
      LOG.warn("This publication job has no path: {}", job);
      return context.result().cancelled();
    }
    String serializedProperties = PublicationJobProperties.getResourceProperties(job);
    Map<String, String> properties = ResourceInfo.deserializeProperties(serializedProperties);
    ResourceInfo resource = new ResourceInfo(path, properties);
    LOG.debug("Processing action: [{} - {}]", action, path);
    try {
      return processPublication(handlerId, action, resource, clientName, context);
    } catch (StreamxPublicationException | StreamxClientException e) {
      LOG.error("Error while processing publication, job will be retried. "
                + "Retry count: {}. "
                + "Number of retries: {}. "
                + "Error message: {}", job.getRetryCount(), job.getNumberOfRetries(), e.getMessage());
      LOG.debug("Publication error details: ", e);
      return context.result().failed(publicationRetryPolicy.getRetryDelay(job));
    } catch (RuntimeException e) {
      LOG.error("Unknown error while processing publication [{}, {}, {}]",
          handlerId, action, path, e);
      return context.result().cancelled();
    }
  }

  private JobExecutionResult processPublication(String handlerId, PublicationAction action, ResourceInfo resource,
      String clientName, JobExecutionContext context)
      throws StreamxPublicationException, StreamxClientException {
    PublicationHandler<?> publicationHandler = findHandler(handlerId);
    if (publicationHandler == null) {
      LOG.warn("Cannot find publication handler with id: [{}]", handlerId);
      return context.result().cancelled();
    }
    StreamxInstanceClient streamxInstanceClient = streamxClientStore.getByName(clientName);
    if (streamxInstanceClient == null) {
      LOG.warn("Cannot find StreamX client with name: [{}]", clientName);
      return context.result().cancelled();
    }

    switch (action) {
      case PUBLISH:
        handlePublish(publicationHandler, streamxInstanceClient, resource);
        break;
      case UNPUBLISH:
        handleUnpublish(publicationHandler, streamxInstanceClient, resource);
        break;
      default:
        LOG.debug("Unsupported publication action: [{}]", action);
        return context.result().cancelled();
    }
    return context.result().succeeded();
  }

  private PublicationHandler<?> findHandler(String handlerId) {
    return publicationHandlerRegistry.getHandlers().stream()
        .filter(handler -> handlerId.equals(handler.getId()))
        .findFirst()
        .orElse(null);
  }

  private <T> void handlePublish(PublicationHandler<T> publicationHandler,
      StreamxInstanceClient streamxInstanceClient, ResourceInfo resource)
      throws StreamxPublicationException, StreamxClientException {
    PublishData<T> publishData = publicationHandler.getPublishData(resource);
    if (publishData == null) {
      LOG.debug("Publish data returned by [{}] is null", publicationHandler.getClass().getName());
      return;
    }
    Publisher<T> publisher = streamxInstanceClient.getPublisher(publishData);
    Message<T> messageToSend = Message.newPublishMessage(publishData.getKey(), publishData.getModel())
            .withProperties(publishData.getProperties())
            .build();
    publisher.send(messageToSend);
    LOG.info("Published resource: [{}] to [{}: {}]", publishData.getKey(),
        streamxInstanceClient.getName(), publishData.getChannel());
  }

  private <T> void handleUnpublish(PublicationHandler<T> publicationHandler,
      StreamxInstanceClient streamxInstanceClient, ResourceInfo resource)
      throws StreamxPublicationException, StreamxClientException {
    UnpublishData<T> unpublishData = publicationHandler.getUnpublishData(resource);
    if (unpublishData == null) {
      LOG.debug("Unpublish data returned by [{}] is null", publicationHandler.getClass().getName());
      return;
    }
    Publisher<T> publisher = streamxInstanceClient.getPublisher(unpublishData);
    Message<T> messageToSend = Message.<T>newUnpublishMessage(unpublishData.getKey())
        .withProperties(unpublishData.getProperties())
        .build();
    publisher.send(messageToSend);
    LOG.info("Unpublished resource: [{}] from [{}: {}]", unpublishData.getKey(),
        streamxInstanceClient.getName(), unpublishData.getChannel());
  }

}
