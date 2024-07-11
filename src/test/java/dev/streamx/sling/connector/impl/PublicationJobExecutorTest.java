package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_ACTION;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_CLIENT_NAME;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_HANDLER_ID;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublicationRetryPolicy;
import dev.streamx.sling.connector.testing.handlers.FakeThrowablePublicationHandler;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJob;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJobExecutionContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlingContextExtension.class)
class PublicationJobExecutorTest {

  private final SlingContext slingContext = new SlingContext();
  private final PublicationJobExecutor publicationJobExecutor = new PublicationJobExecutor();

  private final StreamxClientConfig streamxClientConfig = getFakeStreamxClientConfig();
  private final FakeThrowablePublicationHandler publicationHandler = new FakeThrowablePublicationHandler();

  @BeforeEach
  public void init() {
    slingContext.registerService(StreamxClientConfig.class, streamxClientConfig);
    slingContext.registerService(PublicationHandler.class, publicationHandler);
    slingContext.registerService(PublicationRetryPolicy.class, new FakePublicationRetryPolicy());
    slingContext.registerService(StreamxClientFactory.class, new FakeStreamxClientFactory());
    slingContext.registerInjectActivateService(new StreamxClientStoreImpl());
    slingContext.registerInjectActivateService(new PublicationHandlerRegistry());
    slingContext.registerInjectActivateService(publicationJobExecutor);
  }

  @Test
  void shouldExecuteTheJobWithSuccess() {
    JobExecutionResult result = publicationJobExecutor.process(getFakeJob(),
        new FakeJobExecutionContext());

    assertThat(result.succeeded()).isTrue();
  }

  @Test
  void shouldCancelTheJobOnRuntimeException() {
    publicationHandler.throwRuntimeException();
    JobExecutionResult result = publicationJobExecutor.process(getFakeJob(),
        new FakeJobExecutionContext());

    assertThat(result.cancelled()).isTrue();
  }

  @Test
  void shouldSetRetryDelayOnFailure() {
    publicationHandler.throwException();
    JobExecutionResult result = publicationJobExecutor.process(getFakeJob(),
        new FakeJobExecutionContext());

    assertThat(result.failed()).isTrue();
    assertThat(result.getRetryDelayInMs()).isEqualTo(1000);
  }

  private static FakeStreamxClientConfig getFakeStreamxClientConfig() {
    return new FakeStreamxClientConfig("/fake/streamx/instance", Collections.singletonList(".*"));
  }

  private static Job getFakeJob() {
    Map<String, Object> properties = new HashMap<>();
    properties.put(PN_STREAMX_PATH, "/resource/path/");
    properties.put(PN_STREAMX_HANDLER_ID, "fake-handler");
    properties.put(PN_STREAMX_CLIENT_NAME, "/fake/streamx/instance");
    properties.put(PN_STREAMX_ACTION, PublicationAction.PUBLISH.name());
    return new FakeJob(PublicationJobExecutor.JOB_TOPIC, properties);
  }

}