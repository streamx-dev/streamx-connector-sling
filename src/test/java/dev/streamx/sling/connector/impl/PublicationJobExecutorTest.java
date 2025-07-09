package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_ACTION;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_CLIENT_NAME;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_HANDLER_ID;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_PATH;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_PROPERTIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import dev.streamx.clients.ingestion.exceptions.StreamxClientException;
import dev.streamx.clients.ingestion.publisher.Message;
import dev.streamx.clients.ingestion.publisher.Publisher;
import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.testing.handlers.FakeThrowablePublicationHandler;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJobExecutionContext;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeRetriedJob;
import dev.streamx.sling.connector.testing.streamx.clients.ingestion.FakeStreamxClient;
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
import org.mockito.ArgumentMatchers;

@ExtendWith(SlingContextExtension.class)
class PublicationJobExecutorTest {

  private static final String STREAMX_URL = "/fake/streamx/instance";
  private static final String RESOURCE_PATH = "/resource/path/";

  private final SlingContext slingContext = new SlingContext();
  private final PublicationJobExecutor publicationJobExecutor = new PublicationJobExecutor();

  private final StreamxClientConfig streamxClientConfig = getFakeStreamxClientConfig();
  private final FakeThrowablePublicationHandler publicationHandler = new FakeThrowablePublicationHandler();
  private final FakeStreamxClientFactory fakeStreamxClientFactory = new FakeStreamxClientFactory();
  private final FakeJobExecutionContext fakeJobExecutionContext = new FakeJobExecutionContext();

  @BeforeEach
  public void init() {
    slingContext.registerService(StreamxClientConfig.class, streamxClientConfig);
    slingContext.registerService(PublicationHandler.class, publicationHandler);
    slingContext.registerService(StreamxClientFactory.class, fakeStreamxClientFactory);

    slingContext.registerInjectActivateService(new DefaultPublicationRetryPolicy());
    slingContext.registerInjectActivateService(StreamxClientStoreImpl.class);
    slingContext.registerInjectActivateService(new PublicationHandlerRegistry());
    slingContext.registerInjectActivateService(publicationJobExecutor);
  }

  @Test
  void shouldExecutePublishJobWithSuccess() throws StreamxClientException {
    JobExecutionResult result = publicationJobExecutor.process(
        getFakeJob(PublicationAction.PUBLISH),
        fakeJobExecutionContext);

    assertThat(result.succeeded()).isTrue();
    assertSentMessage(Message.PUBLISH_ACTION, "Success");
  }

  @Test
  void shouldExecuteUnpublishJobWithSuccess() throws StreamxClientException {
    JobExecutionResult result = publicationJobExecutor.process(
        getFakeJob(PublicationAction.UNPUBLISH),
        fakeJobExecutionContext);

    assertThat(result.succeeded()).isTrue();
    assertSentMessage(Message.UNPUBLISH_ACTION, null);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void assertSentMessage(String action, String payload) throws StreamxClientException {
    FakeStreamxClient fakeStreamxClient = fakeStreamxClientFactory.getFakeClient(STREAMX_URL);
    Publisher<?> publisher = fakeStreamxClient.getLastPublisher();
    verify(publisher).send(ArgumentMatchers.<Message>argThat(message -> assertMessage(message, action, payload)));
  }

  private boolean assertMessage(Message<?> message, String action, String payload) {
    assertThat(message.getKey()).isEqualTo(RESOURCE_PATH);
    assertThat(message.getAction()).isEqualTo(action);
    assertThat(message.getPayload()).isEqualTo(payload);
    assertThat(message.getProperties()).containsEntry("test-property-name", "test value");
    return true;
  }

  @Test
  void shouldCancelTheJobOnRuntimeException() {
    publicationHandler.setThrowRuntimeException();
    JobExecutionResult result = publicationJobExecutor.process(
        getFakeJob(PublicationAction.PUBLISH),
        fakeJobExecutionContext);

    assertThat(result.cancelled()).isTrue();
  }

  @ParameterizedTest
  @MethodSource("retryDelay")
  void shouldIncreaseRetryDelayUntilReachingThreshold(int retries, int expectedRetryDelay) {
    publicationHandler.setThrowException();
    JobExecutionResult result = publicationJobExecutor.process(
        getFakeJob(PublicationAction.PUBLISH, retries),
        fakeJobExecutionContext);

    assertThat(result.failed()).isTrue();
    assertThat(result.getRetryDelayInMs()).isEqualTo(expectedRetryDelay);
  }

  private static FakeStreamxClientConfig getFakeStreamxClientConfig() {
    return new FakeStreamxClientConfig(STREAMX_URL, Collections.singletonList(".*"));
  }

  private static Job getFakeJob(PublicationAction action) {
    return getFakeJob(action, 0);
  }

  private static Job getFakeJob(PublicationAction action, int retries) {
    Map<String, Object> properties = new HashMap<>();
    properties.put(PN_STREAMX_PUBLICATION_PATH, RESOURCE_PATH);
    properties.put(PN_STREAMX_PUBLICATION_PROPERTIES, "{}");
    properties.put(PN_STREAMX_PUBLICATION_HANDLER_ID, "fake-handler");
    properties.put(PN_STREAMX_PUBLICATION_CLIENT_NAME, STREAMX_URL);
    properties.put(PN_STREAMX_PUBLICATION_ACTION, action.name());
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
