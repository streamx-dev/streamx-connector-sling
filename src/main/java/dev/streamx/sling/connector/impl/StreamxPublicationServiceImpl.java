package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.IngestionTriggerJobExecutor.PN_STREAMX_INGESTION_ACTION;
import static dev.streamx.sling.connector.impl.IngestionTriggerJobExecutor.PN_STREAMX_INGESTION_RESOURCES;

import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.StreamxPublicationService;
import dev.streamx.sling.connector.impl.StreamxPublicationServiceImpl.Config;
import java.util.List;
import java.util.Map;
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

/**
 * Default {@link StreamxPublicationService}.
 */
@Component(
    service = StreamxPublicationService.class,
    immediate = true
)
@Designate(ocd = Config.class)
public class StreamxPublicationServiceImpl implements StreamxPublicationService {

  private static final Logger LOG = LoggerFactory.getLogger(StreamxPublicationServiceImpl.class);

  @Reference
  private JobManager jobManager;

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
  public void publish(List<ResourceInfo> resourcesToPublish) throws StreamxPublicationException {
    submitIngestionTriggerJob(PublicationAction.PUBLISH, resourcesToPublish);
  }

  @Override
  public void unpublish(List<ResourceInfo> resourcesToUnpublish) throws StreamxPublicationException {
    submitIngestionTriggerJob(PublicationAction.UNPUBLISH, resourcesToUnpublish);
  }

  private void submitIngestionTriggerJob(PublicationAction ingestionAction, List<ResourceInfo> resources) {
    if (!enabled) {
      return;
    }

    Map<String, Object> jobProps = Map.of(
        PN_STREAMX_INGESTION_ACTION, ingestionAction.toString(),
        PN_STREAMX_INGESTION_RESOURCES, resources.stream().map(ResourceInfo::serialize).toArray(String[]::new)
    );

    Job addedJob = jobManager.addJob(IngestionTriggerJobExecutor.JOB_TOPIC, jobProps);
    LOG.debug("Added job: {}", addedJob);
  }

  @ObjectClassDefinition(name = "StreamX Connector Configuration")
  @interface Config {

    @AttributeDefinition(name = "Enable publication to StreamX", description =
        "If the flag is unset the publication requests will be skipped.")
    boolean enabled() default true;
  }
}
