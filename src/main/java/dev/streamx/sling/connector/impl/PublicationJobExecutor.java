package dev.streamx.sling.connector.impl;

import dev.streamx.clients.ingestion.exceptions.StreamxClientException;
import dev.streamx.clients.ingestion.publisher.Publisher;
import dev.streamx.sling.connector.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static dev.streamx.sling.connector.impl.PublicationJobExecutor.JOB_TOPIC;

@Component(
    service = JobExecutor.class,
    property = {JobExecutor.PROPERTY_TOPICS + "=" + JOB_TOPIC}
)
public class PublicationJobExecutor implements JobExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(PublicationJobExecutor.class);

  static final String JOB_TOPIC = "dev/streamx/publications";
  static final String PN_STREAMX_HANDLER_ID = "streamx.handler.id";
  static final String PN_STREAMX_CLIENT_NAME = "streamx.client.name";
  static final String PN_STREAMX_ACTION = "streamx.action";
  static final String PN_STREAMX_PATH = "streamx.path";

  @Reference
  private StreamxClientStore streamxClientStore;

  @Reference
  private PublicationHandlerRegistry publicationHandlerRegistry;

  @Reference
  private PublicationRetryPolicy publicationRetryPolicy;

  @Override
  public JobExecutionResult process(Job job, JobExecutionContext context) {
    LOG.trace("Processing {}", job);
    String handlerId = job.getProperty(PN_STREAMX_HANDLER_ID, String.class);
    String clientName = job.getProperty(PN_STREAMX_CLIENT_NAME, String.class);
    Optional<PublicationAction> actionNullable = PublicationAction.of(job.getProperty(PN_STREAMX_ACTION, String.class));
    if (actionNullable.isEmpty()) {
      LOG.warn("Publication action is not set, job will be cancelled: {}", job);
      return context.result().cancelled();
    }
    PublicationAction action = actionNullable.orElseThrow();
    String path = job.getProperty(PN_STREAMX_PATH, String.class);
    if (StringUtils.isEmpty(path)) {
      LOG.warn("This publication job has no path: {}", job);
      return context.result().cancelled();
    }
    LOG.debug("Processing action: [{} - {}]", action, path);
    try {
      return processPublication(handlerId, action, path, clientName, context);
    } catch (StreamxPublicationException | StreamxClientException e) {
      LOG.error("Error while processing publication, job will be retried. {}", e.getMessage());
      LOG.debug("Publication error details: ", e);
      return context.result().failed(publicationRetryPolicy.getRetryDelay(job));
    } catch (RuntimeException e) {
      LOG.error("Unknown error while processing publication [{}, {}, {}]",
          handlerId, action, path, e);
      return context.result().cancelled();
    }
  }

  private JobExecutionResult processPublication(String handlerId, PublicationAction action, String path,
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
        handlePublish(publicationHandler, streamxInstanceClient, path);
        break;
      case UNPUBLISH:
        handleUnpublish(publicationHandler, streamxInstanceClient, path);
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

}
