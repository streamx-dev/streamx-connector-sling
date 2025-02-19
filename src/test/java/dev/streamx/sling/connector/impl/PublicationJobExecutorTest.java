package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_INGESTION_TYPE;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_CLIENT_NAME;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_HANDLER_ID;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import dev.streamx.sling.connector.IngestionActionType;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.testing.handlers.FakeThrowablePublicationHandler;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJobExecutionContext;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeRetriedJob;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
    slingContext.registerService(StreamxClientFactory.class, new FakeStreamxClientFactory());

    slingContext.registerInjectActivateService(new DefaultPublicationRetryPolicy());
    slingContext.registerInjectActivateService(StreamxClientStoreImpl.class);
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

  @ParameterizedTest
  @MethodSource("retryDelay")
  void shouldIncreaseRetryDelayUntilReachingThreshold(int retries, int expectedRetryDelay) {
    publicationHandler.throwException();
    JobExecutionResult result = publicationJobExecutor.process(getFakeJob(retries),
        new FakeJobExecutionContext());

    assertThat(result.failed()).isTrue();
    assertThat(result.getRetryDelayInMs()).isEqualTo(expectedRetryDelay);
  }

  private static FakeStreamxClientConfig getFakeStreamxClientConfig() {
    return new FakeStreamxClientConfig("/fake/streamx/instance", Collections.singletonList(".*"));
  }

  private static Job getFakeJob() {
    return getFakeJob(0);
  }
  private static Job getFakeJob(int retries) {
    Map<String, Object> properties = new HashMap<>();
    properties.put(PN_STREAMX_PATH, "/resource/path/");
    properties.put(PN_STREAMX_HANDLER_ID, "fake-handler");
    properties.put(PN_STREAMX_CLIENT_NAME, "/fake/streamx/instance");
    properties.put(PN_STREAMX_INGESTION_TYPE, IngestionActionType.PUBLISH.name());
    return new FakeRetriedJob(PublicationJobExecutor.JOB_TOPIC, properties, retries);
  }

  private static Stream<Arguments> retryDelay() {
    return Stream.of(
        Arguments.of(0, 2000),
        Arguments.of(1, 4000),
        Arguments.of(2, 8000),
        Arguments.of(3, 16000),
        Arguments.of(4, 32000),
        Arguments.of(5, 60000),
        Arguments.of(10, 60000),
        Arguments.of(Integer.MAX_VALUE, 60000)
    );
  }

}
