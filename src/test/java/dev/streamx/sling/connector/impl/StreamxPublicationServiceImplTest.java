package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.*;
import dev.streamx.sling.connector.testing.handlers.AssetPublicationHandler;
import dev.streamx.sling.connector.testing.handlers.ImpostorPublicationHandler;
import dev.streamx.sling.connector.testing.handlers.OtherPagePublicationHandler;
import dev.streamx.sling.connector.testing.handlers.PagePublicationHandler;
import dev.streamx.sling.connector.testing.selectors.RelatedPagesSelector;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJob;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJobExecutionContext;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJobManager;
import dev.streamx.sling.connector.testing.streamx.clients.ingestion.FakeStreamxClient;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.assertj.core.groups.Tuple;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssumptions.given;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(SlingContextExtension.class)
class StreamxPublicationServiceImplTest {

  private final SlingContext slingContext = new SlingContext();
  private final ResourceResolver resourceResolver = slingContext.resourceResolver();
  private final Map<String, Object> publicationServiceConfig = new HashMap<>();
  private final List<PublicationHandler<?>> handlers = new ArrayList<>();
  private final List<RelatedResourcesSelector> relatedResourcesSelectors = new ArrayList<>();
  private final List<FakeStreamxClientConfig> fakeStreamxClientConfigs = new ArrayList<>();

  private StreamxPublicationServiceImpl publicationService;
  private FakeJobManager fakeJobManager;
  private FakeStreamxClient fakeStreamxClient;
  private FakeStreamxClientFactory fakeStreamxClientFactory;

  @BeforeEach
  void setUp() {
    handlers.add(new PagePublicationHandler(resourceResolver));
    handlers.add(new AssetPublicationHandler(resourceResolver));
    fakeStreamxClientConfigs.add(getDefaultFakeStreamxClientConfig());
  }

  @SuppressWarnings("ReturnOfNull")
  private void initializeComponentsIfNotInitialized() throws StreamxPublicationException {
    if (publicationService != null) {
      return;
    }

    publicationService = spy(new StreamxPublicationServiceImpl());
    doAnswer(
        invocation -> {
          processResourcesAsJob(invocation.getArgument(0), "PUBLISH");
          return null;
        }
    ).when(publicationService).publish(anyList());

    doAnswer(
        invocation -> {
          processResourcesAsJob(invocation.getArgument(0), "UNPUBLISH");
          return null;
        }
    ).when(publicationService).unpublish(anyList());

    JobExecutor publicationJobExecutor = new PublicationJobExecutor();

    for (FakeStreamxClientConfig config : fakeStreamxClientConfigs) {
      slingContext.registerService(StreamxClientConfig.class, config);
    }

    fakeStreamxClientFactory = new FakeStreamxClientFactory();
    slingContext.registerService(StreamxClientFactory.class, fakeStreamxClientFactory);
    fakeJobManager = new FakeJobManager(Collections.singletonList(publicationJobExecutor));
    slingContext.registerService(JobManager.class, fakeJobManager);
    slingContext.registerService(PublicationRetryPolicy.class, new DefaultPublicationRetryPolicy());
    for (PublicationHandler<?> handler : handlers) {
      slingContext.registerService(PublicationHandler.class, handler);
    }
    for (RelatedResourcesSelector selector : relatedResourcesSelectors) {
      slingContext.registerService(RelatedResourcesSelector.class, selector);
    }
    slingContext.registerInjectActivateService(StreamxClientStoreImpl.class);
    slingContext.registerInjectActivateService(new PublicationHandlerRegistry());

    slingContext.registerInjectActivateService(new RelatedResourcesSelectorRegistry());

    slingContext.registerInjectActivateService(publicationService, publicationServiceConfig);
    slingContext.registerInjectActivateService(publicationJobExecutor);

    fakeStreamxClient = fakeStreamxClientFactory.getFakeClient("/fake/streamx/instance");
  }

  private void processResourcesAsJob(List<ResourceInfo> resources, String action) {
    String[] serializedResources = resources
        .stream()
        .map(ResourceInfo::serialize)
        .toArray(String[]::new);

    Job job = mock(Job.class);
    when(job.getProperty(IngestionTrigger.PN_STREAMX_RESOURCES_INFO, String[].class)).thenReturn(serializedResources);
    when(job.getProperty(IngestionTrigger.PN_STREAMX_INGESTION_ACTION, String.class)).thenReturn(action);
    publicationService.process(job, new FakeJobExecutionContext());
  }

  @Test
  void shouldPublishSinglePage() throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathIsPublished("/content/my-site/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages", "Page: page-1")
    );
  }

  @Test
  void shouldPublishMultiplePages() throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathsArePublished("/content/my-site/page-1", "/content/my-site/page-1/page-2");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(2);
    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages", "Page: page-1"),
        publish("/content/my-site/page-1/page-2.html", "pages", "Page: page-2")
    );
  }

  @Test
  void shouldUnpublishSinglePage() throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathIsUnpublished("/content/my-site/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenPublicationsContainsExactly(
        unpublish("/content/my-site/page-1.html", "pages")
    );
  }

  @Test
  void shouldUnpublishMultiplePages() throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathsAreUnpublished("/content/my-site/page-1", "/content/my-site/page-1/page-2");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(2);
    thenPublicationsContainsExactly(
        unpublish("/content/my-site/page-1.html", "pages"),
        unpublish("/content/my-site/page-1/page-2.html", "pages")
    );
  }

  @Test
  void shouldUnpublishEvenIfResourceDoesNotExist() throws StreamxPublicationException {
    given(resourceResolver.getResource("/content/my-site/page-1.html")).isNull();
    given(resourceResolver.getResource("/content/dam/asset-1.jpeg")).isNull();

    whenPathsAreUnpublished("/content/my-site/page-1", "/content/dam/asset-1.jpeg");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(2);
    thenPublicationsContainsExactly(
        unpublish("/content/my-site/page-1.html", "pages"),
        unpublish("/content/dam/asset-1.jpeg", "assets")
    );
  }

  @Test
  void shouldNotPublishIfIsDisabled() throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");
    givenPublicationService(config ->
        config.put("enabled", false)
    );

    whenPathsArePublished("/content/my-site/page-1", "/content/my-site/page-1/page-2");
    whenPathIsUnpublished("/content/my-site/page-1/page-2");
    whenAllJobsAreProcessed();

    then(publicationService.isEnabled()).isFalse();
    thenProcessedJobsCountIs(0);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldNotPublishIfResourceDoesNotExist()
      throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1");

    whenPathIsPublished("/content/my-site/page-1/non-existing-page");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldNotPublishIfNoPublishDataWasReturnedByHandler()
      throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1");
    givenPageHierarchy("/content/impostor-site/page-2");
    givenHandlers(
        new PagePublicationHandler(resourceResolver),
        new ImpostorPublicationHandler()
    );

    whenPathsArePublished("/content/my-site/page-1", "/content/impostor-site/page-2");
    whenPathsAreUnpublished("/content/my-site/page-1", "/content/impostor-site/page-2");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(4);
    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages", "Page: page-1"),
        unpublish("/content/my-site/page-1.html", "pages")
    );
  }

  @Test
  void shouldNotPublishIfPathsAreEmpty() throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1");

    whenPathsArePublished("", "/content/my-site/page-1", null);
    whenPathsAreUnpublished(null, "/content/my-site/page-1", "");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(2);
    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages", "Page: page-1"),
        unpublish("/content/my-site/page-1.html", "pages")
    );
  }

  @Test
  void shouldNotPublishIfNoPathsWereGiven()
      throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1");

    whenPathsArePublished();
    whenPathsAreUnpublished();
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(0);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldNotPublishIfNoHandlerCanHandle()
      throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/var/my-site-copy/page-1");

    whenPathIsPublished("/var/my-site-copy/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(0);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldPublishDifferentTypesOfContent()
      throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1/page-2");
    givenAsset("/content/dam/asset-1.jpeg");

    whenPathsArePublished(
        "/content/my-site/page-1",
        "/content/dam/asset-1.jpeg",
        "/content/my-site/page-1/page-2"
    );
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(3);
    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages", "Page: page-1"),
        publish("/content/dam/asset-1.jpeg", "assets", "Asset: asset-1.jpeg"),
        publish("/content/my-site/page-1/page-2.html", "pages", "Page: page-2")
    );
  }

  @Test
  void shouldNotPublishIfResourceWasRemovedAfterPublication()
      throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1");

    whenPathIsPublished("/content/my-site/page-1");
    whenResourceIsRemoved("/content/my-site/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldCreatePublishJobForEachInstance()
      throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1");

    givenStreamxClientInstances(
        getDefaultFakeStreamxClientConfig(),
        getMySiteFakeStreamxClientConfig()
    );

    whenPathsArePublished(
        "/content/my-site/page-1"
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(2);
  }

  @Test
  void shouldPublishToStreamxInstanceIfPathMatchesPattern()
      throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1");
    givenPageHierarchy("/content/other-site/page-1");
    givenAsset("/content/dam/asset-1.jpeg");

    givenHandlers(
        new PagePublicationHandler(resourceResolver),
        new OtherPagePublicationHandler(resourceResolver),
        new AssetPublicationHandler(resourceResolver)
    );

    givenStreamxClientInstances(
        getDefaultFakeStreamxClientConfig(),
        getMySiteFakeStreamxClientConfig(),
        getOtherSiteFakeStreamxClientConfig()
    );

    whenPathsArePublished(
        "/content/my-site/page-1",
        "/content/other-site/page-1",
        "/content/dam/asset-1.jpeg"
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(7);

    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages", "Page: page-1"),
        publish("/content/other-site/page-1.html", "pages", "Page: page-1"),
        publish("/content/dam/asset-1.jpeg", "assets", "Asset: asset-1.jpeg")
    );

    thenInstancePublicationsContainsExactly(
        "/fake/my-site/instance",
        publish("/content/my-site/page-1.html", "pages", "Page: page-1"),
        publish("/content/dam/asset-1.jpeg", "assets", "Asset: asset-1.jpeg")
    );

    thenInstancePublicationsContainsExactly(
        "/fake/other-site/instance",
        publish("/content/other-site/page-1.html", "pages", "Page: page-1"),
        publish("/content/dam/asset-1.jpeg", "assets", "Asset: asset-1.jpeg")
    );
  }

  @Test
  void shouldUpdateContentOnStreamxForRelatedResources()
      throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1");
    givenPageHierarchy("/content/my-site/related-page-to-publish");
    givenPageHierarchy("/content/my-site/other-related-page-to-publish");

    givenRelatedResourcesSelectors(new RelatedPagesSelector());

    whenPathsArePublished(
        "/content/my-site/page-1"
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(3);

    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages", "Page: page-1"),
        publish("/content/my-site/related-page-to-publish.html", "pages",
            "Page: related-page-to-publish"),
        publish("/content/my-site/other-related-page-to-publish.html", "pages",
            "Page: other-related-page-to-publish")
    );
  }

  @Test
  void shouldUpdateRelatedResourcesJustOnceEvenIfWillBeReturnedByMultipleSelectors()
      throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1");
    givenPageHierarchy("/content/my-site/related-page-to-publish");
    givenPageHierarchy("/content/my-site/other-related-page-to-publish");

    givenRelatedResourcesSelectors(new RelatedPagesSelector(), new RelatedPagesSelector(),
        new RelatedPagesSelector());

    whenPathsArePublished(
        "/content/my-site/page-1"
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(3);

    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages", "Page: page-1"),
        publish("/content/my-site/related-page-to-publish.html", "pages",
            "Page: related-page-to-publish"),
        publish("/content/my-site/other-related-page-to-publish.html", "pages",
            "Page: other-related-page-to-publish")
    );
  }

  @Test
  void shouldUpdateRelatedResourcesJustOnceEvenIfRelatesToMultiplePublishedResources()
      throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1");
    givenPageHierarchy("/content/my-site/page-2");
    givenPageHierarchy("/content/my-site/related-page-to-publish");
    givenPageHierarchy("/content/my-site/other-related-page-to-publish");

    givenRelatedResourcesSelectors(new RelatedPagesSelector());

    whenPathsArePublished(
        "/content/my-site/page-1",
        "/content/my-site/page-2"
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(4);

    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages", "Page: page-1"),
        publish("/content/my-site/page-2.html", "pages", "Page: page-2"),
        publish("/content/my-site/related-page-to-publish.html", "pages",
            "Page: related-page-to-publish"),
        publish("/content/my-site/other-related-page-to-publish.html", "pages",
            "Page: other-related-page-to-publish")
    );
  }

  @Test
  void shouldNotSendExtraUpdateToRelatedResourcesIfItIsPublishedExplicitly()
      throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1");
    givenPageHierarchy("/content/my-site/related-page-to-publish");
    givenPageHierarchy("/content/my-site/other-related-page-to-publish");

    givenRelatedResourcesSelectors(new RelatedPagesSelector());

    whenPathsArePublished(
        "/content/my-site/page-1",
        "/content/my-site/related-page-to-publish"
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(3);

    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages", "Page: page-1"),
        publish("/content/my-site/related-page-to-publish.html", "pages",
            "Page: related-page-to-publish"),
        publish("/content/my-site/other-related-page-to-publish.html", "pages",
            "Page: other-related-page-to-publish")
    );
  }

  @Test
  void shouldSubmitIngestionTriggerJobs() throws Exception {
    // given
    initializeComponentsIfNotInitialized();
    doCallRealMethod().when(publicationService).publish(anyList());
    doCallRealMethod().when(publicationService).unpublish(anyList());

    // when
    publicationService.publish(Collections.singletonList(new ResourceInfo("path-1", "type-1")));
    publicationService.unpublish(Collections.singletonList(new ResourceInfo("path-2", "type-2")));

    // then
    List<FakeJob> queuedJobs = fakeJobManager.getJobQueue();
    assertThat(queuedJobs).hasSize(2);

    FakeJob publishJob = queuedJobs.get(0);
    assertThat(publishJob.getProperty(IngestionTrigger.PN_STREAMX_INGESTION_ACTION, String.class))
        .isEqualTo("PUBLISH");
    assertThat(publishJob.getProperty(IngestionTrigger.PN_STREAMX_RESOURCES_INFO, String[].class))
        .containsExactly("{\"path\":\"path-1\",\"primaryNodeType\":\"type-1\"}");

    FakeJob unpublishJob = queuedJobs.get(1);
    assertThat(unpublishJob.getProperty(IngestionTrigger.PN_STREAMX_INGESTION_ACTION, String.class))
        .isEqualTo("UNPUBLISH");
    assertThat(unpublishJob.getProperty(IngestionTrigger.PN_STREAMX_RESOURCES_INFO, String[].class))
        .containsExactly("{\"path\":\"path-2\",\"primaryNodeType\":\"type-2\"}");
  }

  private void givenPageHierarchy(String path) throws PersistenceException {
    slingContext.create().resource(path);
    resourceResolver.commit();
  }

  private void givenAsset(String path) throws PersistenceException {
    slingContext.create().resource(path);
    resourceResolver.commit();
  }

  private void givenPublicationService(Consumer<Map<String, Object>> propertiesModifier) {
    propertiesModifier.accept(publicationServiceConfig);
  }

  private void givenRelatedResourcesSelectors(RelatedResourcesSelector... selectors) {
    this.relatedResourcesSelectors.clear();
    this.relatedResourcesSelectors.addAll(Arrays.asList(selectors));
  }

  private void givenHandlers(PublicationHandler<?>... handlers) {
    this.handlers.clear();
    this.handlers.addAll(Arrays.asList(handlers));
  }

  private void givenStreamxClientInstances(FakeStreamxClientConfig... configs) {
    this.fakeStreamxClientConfigs.clear();
    this.fakeStreamxClientConfigs.addAll(Arrays.asList(configs));
  }

  private void whenPathIsPublished(String path) throws StreamxPublicationException {
    initializeComponentsIfNotInitialized();
    publicationService.publish(toResourceInfoList(path));
  }

  private void whenPathsArePublished(String... paths) throws StreamxPublicationException {
    initializeComponentsIfNotInitialized();
    publicationService.publish(toResourceInfoList(paths));
  }

  private void whenPathIsUnpublished(String path) throws StreamxPublicationException {
    initializeComponentsIfNotInitialized();
    publicationService.unpublish(toResourceInfoList(path));
  }

  private void whenPathsAreUnpublished(String... paths) throws StreamxPublicationException {
    initializeComponentsIfNotInitialized();
    publicationService.unpublish(toResourceInfoList(paths));
  }

  private static List<ResourceInfo> toResourceInfoList(String... paths) {
    return Arrays.stream(paths).map(path -> new ResourceInfo(
        path,
        StringUtils.contains(path, "/dam/") ? "dam:Asset" : "cq:Page"
    )).collect(Collectors.toList());
  }

  private void whenAllJobsAreProcessed() {
    fakeJobManager.processAllJobs();
  }

  private void whenResourceIsRemoved(String path) throws PersistenceException {
    Resource resource = resourceResolver.getResource(path);
    assertThat(resource).isNotNull();
    resourceResolver.delete(resource);
    resourceResolver.commit();
  }

  private void thenProcessedJobsCountIs(int count) {
    assertThat(fakeJobManager.getProcessedJobsCount()).isEqualTo(count);
  }

  private void thenPublicationsContainsExactly(Tuple... tuples) {
    assertThat(fakeStreamxClient.getPublications())
        .extracting("action", "key", "channel", "data")
        .containsExactly(tuples);
  }

  private void thenInstancePublicationsContainsExactly(String streamxInstanceUrl, Tuple... tuples) {
    FakeStreamxClient streamxClient = fakeStreamxClientFactory.getFakeClient(streamxInstanceUrl);
    assertThat(streamxClient.getPublications())
        .extracting("action", "key", "channel", "data")
        .containsExactly(tuples);
  }

  private void thenNoPublicationsWereMade() {
    assertThat(fakeStreamxClient.getPublications()).isEmpty();
  }

  private Tuple publish(String key, String channel, String data) {
    return tuple("Publish", key, channel, data);
  }

  private Tuple unpublish(String key, String channel) {
    return tuple("Unpublish", key, channel, null);
  }

  @NotNull
  private static FakeStreamxClientConfig getOtherSiteFakeStreamxClientConfig() {
    return new FakeStreamxClientConfig("/fake/other-site/instance",
        Arrays.asList("/.*/other-site/.*", "/.*/dam/.*"));
  }

  @NotNull
  private static FakeStreamxClientConfig getMySiteFakeStreamxClientConfig() {
    return new FakeStreamxClientConfig("/fake/my-site/instance",
        Arrays.asList("/.*/my-site/.*", "/.*/dam/.*"));
  }

  @NotNull
  private static FakeStreamxClientConfig getDefaultFakeStreamxClientConfig() {
    return new FakeStreamxClientConfig("/fake/streamx/instance", Collections.singletonList(".*"));
  }
}
