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
    Optional<IngestionActionType> ingestionActionTypeOptional = IngestionActionType.of(
        job.getProperty(PN_STREAMX_ACTION, String.class));
    if (ingestionActionTypeOptional.isEmpty()) {
      LOG.warn("Publication action is not set, job will be cancelled: {}", job);
      return context.result().cancelled();
    }
    IngestionActionType ingestionActionType = ingestionActionTypeOptional.orElseThrow();
    String path = job.getProperty(PN_STREAMX_PATH, String.class);
    if (StringUtils.isEmpty(path)) {
      LOG.warn("This publication job has no path: {}", job);
      return context.result().cancelled();
    }
    LOG.debug("Processing action: [{} - {}]", ingestionActionType, path);
    try {
      return processPublication(handlerId, ingestionActionType, path, clientName, context);
    } catch (StreamxPublicationException | StreamxClientException e) {
      LOG.error("Error while processing publication, job will be retried. {}", e.getMessage());
      LOG.debug("Publication error details: ", e);
      return context.result().failed(publicationRetryPolicy.getRetryDelay(job));
    } catch (RuntimeException e) {
      LOG.error("Unknown error while processing publication [{}, {}, {}]",
          handlerId, ingestionActionType, path, e);
      return context.result().cancelled();
    }
  }

  private JobExecutionResult processPublication(String handlerId,
      IngestionActionType ingestionActionType,
      String path,
      String clientName, JobExecutionContext context)
      throws StreamxPublicationException, StreamxClientException {
    IngestionDataFactory<?> ingestionDataFactory = findHandler(handlerId);
    if (ingestionDataFactory == null) {
      LOG.warn("Cannot find publication handler with id: [{}]", handlerId);
      return context.result().cancelled();
    }
    StreamxInstanceClient streamxInstanceClient = streamxClientStore.getByName(clientName);
    if (streamxInstanceClient == null) {
      LOG.warn("Cannot find StreamX client with name: [{}]", clientName);
      return context.result().cancelled();
    }

    switch (ingestionActionType) {
      case PUBLISH:
        handlePublish(ingestionDataFactory, streamxInstanceClient, path);
        break;
      case UNPUBLISH:
        handleUnpublish(ingestionDataFactory, streamxInstanceClient, path);
        break;
      default:
        LOG.debug("Unsupported publication action: [{}]", ingestionActionType);
        return context.result().cancelled();
    }
    return context.result().succeeded();
  }

  private IngestionDataFactory<?> findHandler(String handlerId) {
    return publicationHandlerRegistry.getHandlers().stream()
        .filter(handler -> handlerId.equals(handler.getId()))
        .findFirst()
        .orElse(null);
  }

  private void handlePublish(IngestionDataFactory<?> ingestionDataFactory,
      StreamxInstanceClient streamxInstanceClient, String resourcePath)
      throws StreamxPublicationException, StreamxClientException {
    PublishData<?> publishData = ingestionDataFactory.producePublishData(() -> resourcePath);
    if (publishData == null) {
      LOG.debug("Publish data returned by [{}] is null", ingestionDataFactory.getClass().getName());
      return;
    }
    handlePublish(publishData, streamxInstanceClient);
  }

  private <T> void handlePublish(PublishData<T> publishData,
      StreamxInstanceClient streamxInstanceClient)
      throws StreamxClientException {
    Publisher<T> publisher = streamxInstanceClient.getPublisher(publishData);
    publisher.publish(publishData.key().get(), publishData.model());
    LOG.info("Published resource: [{}] to [{}: {}]", publishData.key(),
        streamxInstanceClient.getName(), publishData.channel());
  }

  private void handleUnpublish(IngestionDataFactory<?> ingestionDataFactory,
      StreamxInstanceClient streamxInstanceClient, String resourcePath)
      throws StreamxPublicationException, StreamxClientException {
    UnpublishData<?> unpublishData = ingestionDataFactory.produceUnpublishData(() -> resourcePath);
    if (unpublishData == null) {
      LOG.debug("Unpublish data returned by [{}] is null",
          ingestionDataFactory.getClass().getName());
      return;
    }
    handleUnpublish(unpublishData, streamxInstanceClient);
  }

  private <T> void handleUnpublish(IngestionData<T> unpublishData,
      StreamxInstanceClient streamxInstanceClient)
      throws StreamxClientException {
    Publisher<T> publisher = streamxInstanceClient.getPublisher(unpublishData);
    publisher.unpublish(unpublishData.key().get());
    LOG.info("Unpublished resource: [{}] from [{}: {}]", unpublishData.key(),
        streamxInstanceClient.getName(), unpublishData.channel());
  }

}
