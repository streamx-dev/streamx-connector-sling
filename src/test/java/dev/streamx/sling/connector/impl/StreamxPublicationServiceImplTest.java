package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.*;
import dev.streamx.sling.connector.test.util.RandomBytesWriter;
import dev.streamx.sling.connector.testing.handlers.AssetPublicationHandler;
import dev.streamx.sling.connector.testing.handlers.ImpostorPublicationHandler;
import dev.streamx.sling.connector.testing.handlers.OtherPagePublicationHandler;
import dev.streamx.sling.connector.testing.handlers.PagePublicationHandler;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJob;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJobExecutionContext;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJobManager;
import dev.streamx.sling.connector.testing.streamx.clients.ingestion.FakeStreamxClient;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssumptions.given;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(SlingContextExtension.class)
class StreamxPublicationServiceImplTest {

  private static final String PAGES_CHANNEL = "pages";
  private static final String ASSETS_CHANNEL = "assets";
  private static final String RELATED_PAGE_TO_PUBLISH = "/content/my-site/related-page-to-publish";
  private static final String OTHER_RELATED_PAGE_TO_PUBLISH = "/content/my-site/other-related-page-to-publish";

  private final SlingContext slingContext = new SlingContext(ResourceResolverType.JCR_OAK);
  private final ResourceResolver resourceResolver = spy(slingContext.resourceResolver());
  private final ResourceResolverFactory resourceResolverFactory = mock(ResourceResolverFactory.class);
  private final Map<String, Object> publicationServiceConfig = new HashMap<>();
  private final List<PublicationHandler<?>> handlers = new ArrayList<>();
  private final List<RelatedResourcesSelector> relatedResourcesSelectors = new ArrayList<>();
  private final List<FakeStreamxClientConfig> fakeStreamxClientConfigs = new ArrayList<>();

  private StreamxPublicationServiceImpl publicationService;
  private FakeJobManager fakeJobManager;
  private FakeStreamxClient fakeStreamxClient;
  private FakeStreamxClientFactory fakeStreamxClientFactory;

  private final SlingRequestProcessor dummyRequestProcessor = (HttpServletRequest request, HttpServletResponse response, ResourceResolver resolver) ->
      RandomBytesWriter.writeRandomBytes(response, 100);

  private final RelatedResourcesSelector relatedPagesSelector = resourcePath ->
       Arrays.asList(
          new ResourceInfo(RELATED_PAGE_TO_PUBLISH, "cq:Page"),
          new ResourceInfo(OTHER_RELATED_PAGE_TO_PUBLISH, "cq:Page")
      );

  @BeforeEach
  void setUp() {
    handlers.add(new PagePublicationHandler(resourceResolver));
    handlers.add(new AssetPublicationHandler(resourceResolver));
    fakeStreamxClientConfigs.add(getDefaultFakeStreamxClientConfig());
  }

  @SuppressWarnings("ReturnOfNull")
  private void initializeComponentsIfNotInitialized() throws LoginException {
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
    fakeJobManager = spy(new FakeJobManager(Collections.singletonList(publicationJobExecutor)));
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

    doReturn(resourceResolver).when(resourceResolverFactory).getAdministrativeResourceResolver(null);
    doNothing().when(resourceResolver).close();

    slingContext.registerService(SlingRequestProcessor.class, dummyRequestProcessor);
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
    when(job.getProperty(IngestionTriggerJobHelper.PN_STREAMX_RESOURCES_INFO, String[].class)).thenReturn(serializedResources);
    when(job.getProperty(IngestionTriggerJobHelper.PN_STREAMX_INGESTION_ACTION, String.class)).thenReturn(action);
    publicationService.process(job, new FakeJobExecutionContext());
  }

  @Test
  void shouldPublishSinglePage() throws Exception {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathIsPublished("/content/my-site/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenPublicationsContainsExactly(
        publishPage("/content/my-site/page-1.html")
    );
  }

  @Test
  void shouldPublishMultiplePages() throws Exception {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathsArePublished("/content/my-site/page-1", "/content/my-site/page-1/page-2");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(2);
    thenPublicationsContainsExactly(
        publishPage("/content/my-site/page-1.html"),
        publishPage("/content/my-site/page-1/page-2.html")
    );
  }

  @Test
  void shouldPublishAndUnpublishSinglePage() throws Exception {
    givenPageHierarchy("/content/my-site/page-1/page-2");

    whenPathIsPublished("/content/my-site/page-1");
    whenPathIsUnpublished("/content/my-site/page-1");

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(2);
    thenPublicationsContainsExactly(
        publishPage("/content/my-site/page-1.html"),
        unpublishPage("/content/my-site/page-1.html")
    );
  }

  @Test
  void shouldUnpublishSinglePage() throws Exception {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathIsUnpublished("/content/my-site/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenPublicationsContainsExactly(
        unpublishPage("/content/my-site/page-1.html")
    );
  }

  @Test
  void shouldUnpublishMultiplePages() throws Exception {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathsAreUnpublished("/content/my-site/page-1", "/content/my-site/page-1/page-2");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(2);
    thenPublicationsContainsExactly(
        unpublishPage("/content/my-site/page-1.html"),
        unpublishPage("/content/my-site/page-1/page-2.html")
    );
  }

  @Test
  void shouldUnpublishEvenIfResourceDoesNotExist() throws Exception {
    given(resourceResolver.getResource("/content/my-site/page-1.html")).isNull();
    given(resourceResolver.getResource("/content/dam/asset-1.jpeg")).isNull();

    whenPathsAreUnpublished("/content/my-site/page-1", "/content/dam/asset-1.jpeg");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(2);
    thenPublicationsContainsExactly(
        unpublishPage("/content/my-site/page-1.html"),
        unpublishAsset("/content/dam/asset-1.jpeg")
    );
  }

  @Test
  void shouldNotPublishIfIsDisabled() throws Exception {
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
  void shouldNotPublishIfResourceDoesNotExist() throws Exception {
    givenPageHierarchy("/content/my-site/page-1");

    whenPathIsPublished("/content/my-site/page-1/non-existing-page");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldNotPublishIfNoPublishDataWasReturnedByHandler() throws Exception {
    givenPageHierarchy(
        "/content/my-site/page-1",
        "/content/impostor-site/page-2"
    );
    givenHandlers(
        new PagePublicationHandler(resourceResolver),
        new ImpostorPublicationHandler()
    );

    whenPathsArePublished("/content/my-site/page-1", "/content/impostor-site/page-2");
    whenPathsAreUnpublished("/content/my-site/page-1", "/content/impostor-site/page-2");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(4);
    thenPublicationsContainsExactly(
        publishPage("/content/my-site/page-1.html"),
        unpublishPage("/content/my-site/page-1.html")
    );
  }

  @Test
  void shouldNotPublishIfAnyPathIsEmpty() throws Exception {
    givenPageHierarchy("/content/my-site/page-1");

    assertThatThrownBy(() -> whenPathsArePublished("/content/my-site/page-1", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("path cannot be blank");

    assertThatThrownBy(() -> whenPathsAreUnpublished("/content/my-site/page-1", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("path cannot be blank");

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(0);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldNotPublishIfNoPathsWereGiven() throws Exception {
    givenPageHierarchy("/content/my-site/page-1");

    whenPathsArePublished();
    whenPathsAreUnpublished();
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(0);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldNotPublishIfNoHandlerCanHandle() throws Exception {
    givenPageHierarchy("/var/my-site-copy/page-1");

    whenPathIsPublished("/var/my-site-copy/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(0);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldPublishDifferentTypesOfContent() throws Exception {
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
        publishPage("/content/my-site/page-1.html"),
        publishAsset("/content/dam/asset-1.jpeg"),
        publishPage("/content/my-site/page-1/page-2.html")
    );
  }

  @Test
  void shouldNotPublishIfResourceWasRemovedAfterPublication() throws Exception {
    givenPageHierarchy("/content/my-site/page-1");

    whenPathIsPublished("/content/my-site/page-1");
    whenResourceIsRemoved("/content/my-site/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldCreatePublishJobForEachInstance() throws Exception {
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
  void shouldPublishToStreamxInstanceIfPathMatchesPattern() throws Exception {
    givenPageHierarchy(
        "/content/my-site/page-1",
        "/content/other-site/page-1"
    );
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
        publishPage("/content/my-site/page-1.html"),
        publishPage("/content/other-site/page-1.html"),
        publishAsset("/content/dam/asset-1.jpeg")
    );

    thenInstancePublicationsContainsExactly(
        "/fake/my-site/instance",
        publishPage("/content/my-site/page-1.html"),
        publishAsset("/content/dam/asset-1.jpeg")
    );

    thenInstancePublicationsContainsExactly(
        "/fake/other-site/instance",
        publishPage("/content/other-site/page-1.html"),
        publishAsset("/content/dam/asset-1.jpeg")
    );
  }

  @Test
  void shouldUpdateContentOnStreamxForRelatedResources() throws Exception {
    givenPageHierarchy(
        "/content/my-site/page-1",
        RELATED_PAGE_TO_PUBLISH,
        OTHER_RELATED_PAGE_TO_PUBLISH
    );

    givenRelatedResourcesSelectors(relatedPagesSelector);

    whenPathsArePublished(
        "/content/my-site/page-1"
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(3);

    thenPublicationsContainsExactly(
        publishPage("/content/my-site/page-1.html"),
        publishPage(RELATED_PAGE_TO_PUBLISH + ".html"),
        publishPage(OTHER_RELATED_PAGE_TO_PUBLISH + ".html")
    );
  }

  @Test
  void shouldUpdateRelatedResourcesJustOnceEvenIfWillBeReturnedByMultipleSelectors() throws Exception {
    givenPageHierarchy(
        "/content/my-site/page-1",
        RELATED_PAGE_TO_PUBLISH,
        OTHER_RELATED_PAGE_TO_PUBLISH
    );

    givenRelatedResourcesSelectors(relatedPagesSelector, relatedPagesSelector, relatedPagesSelector);

    whenPathsArePublished(
        "/content/my-site/page-1"
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(3);

    thenPublicationsContainsExactly(
        publishPage("/content/my-site/page-1.html"),
        publishPage(RELATED_PAGE_TO_PUBLISH + ".html"),
        publishPage(OTHER_RELATED_PAGE_TO_PUBLISH + ".html")
    );
  }

  @Test
  void shouldUpdateRelatedResourcesJustOnceEvenIfRelatesToMultiplePublishedResources() throws Exception {
    givenPageHierarchy(
        "/content/my-site/page-1",
        "/content/my-site/page-2",
        RELATED_PAGE_TO_PUBLISH,
        OTHER_RELATED_PAGE_TO_PUBLISH
    );

    givenRelatedResourcesSelectors(relatedPagesSelector);

    whenPathsArePublished(
        "/content/my-site/page-1",
        "/content/my-site/page-2"
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(4);

    thenPublicationsContainsExactly(
        publishPage("/content/my-site/page-1.html"),
        publishPage("/content/my-site/page-2.html"),
        publishPage(RELATED_PAGE_TO_PUBLISH + ".html"),
        publishPage(OTHER_RELATED_PAGE_TO_PUBLISH + ".html")
    );
  }

  @Test
  void shouldNotSendExtraUpdateToRelatedResourcesIfItIsPublishedExplicitly() throws Exception {
    givenPageHierarchy(
        "/content/my-site/page-1",
        RELATED_PAGE_TO_PUBLISH,
        OTHER_RELATED_PAGE_TO_PUBLISH
    );

    givenRelatedResourcesSelectors(relatedPagesSelector);

    whenPathsArePublished(
        "/content/my-site/page-1",
        RELATED_PAGE_TO_PUBLISH
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(3);

    thenPublicationsContainsExactly(
        publishPage("/content/my-site/page-1.html"),
        publishPage(RELATED_PAGE_TO_PUBLISH + ".html"),
        publishPage(OTHER_RELATED_PAGE_TO_PUBLISH + ".html")
    );
  }

  @Test
  void shouldInternallyHandleExceptionWhileAddingNewSlingJob() throws Exception {
    initializeComponentsIfNotInitialized();
    doReturn(null).when(fakeJobManager).addJob(anyString(), anyMap());

    givenPageHierarchy("/content/my-site/page-1");

    whenPathIsPublished("/content/my-site/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(0);
    thenNoPublicationsWereMade();
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
    assertThat(publishJob.getProperty(IngestionTriggerJobHelper.PN_STREAMX_INGESTION_ACTION, String.class))
        .isEqualTo("PUBLISH");
    assertThat(publishJob.getProperty(IngestionTriggerJobHelper.PN_STREAMX_RESOURCES_INFO, String[].class))
        .containsExactly("{\"path\":\"path-1\",\"primaryNodeType\":\"type-1\"}");

    FakeJob unpublishJob = queuedJobs.get(1);
    assertThat(unpublishJob.getProperty(IngestionTriggerJobHelper.PN_STREAMX_INGESTION_ACTION, String.class))
        .isEqualTo("UNPUBLISH");
    assertThat(unpublishJob.getProperty(IngestionTriggerJobHelper.PN_STREAMX_RESOURCES_INFO, String[].class))
        .containsExactly("{\"path\":\"path-2\",\"primaryNodeType\":\"type-2\"}");
  }

  private void givenPageHierarchy(String... paths) throws PersistenceException {
    for (String path : paths) {
      slingContext.create().resource(path);
    }
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

  private void whenPathIsPublished(String path) throws LoginException {
    initializeComponentsIfNotInitialized();
    publicationService.publish(toResourceInfoList(path));
  }

  private void whenPathsArePublished(String... paths) throws LoginException {
    initializeComponentsIfNotInitialized();
    publicationService.publish(toResourceInfoList(paths));
  }

  private void whenPathIsUnpublished(String path) throws LoginException {
    initializeComponentsIfNotInitialized();
    publicationService.unpublish(toResourceInfoList(path));
  }

  private void whenPathsAreUnpublished(String... paths) throws LoginException {
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

  private Tuple publishPage(String key) {
    return publish(key, PAGES_CHANNEL, "Page: ");
  }

  private Tuple publishAsset(String key) {
    return publish(key, ASSETS_CHANNEL, "Asset: ");
  }

  private Tuple publish(String key, String channel, String dataPrefix) {
    String pageName = StringUtils.substringAfterLast(key, "/");
    String data = dataPrefix + pageName.replace(".html", "");
    return tuple("Publish", key, channel, data);
  }

  private Tuple unpublishPage(String key) {
    return unpublish(key, PAGES_CHANNEL);
  }

  private Tuple unpublishAsset(String key) {
    return unpublish(key, ASSETS_CHANNEL);
  }

  private Tuple unpublish(String key, String channel) {
    return tuple("Unpublish", key, channel, null);
  }

  private static FakeStreamxClientConfig getOtherSiteFakeStreamxClientConfig() {
    return new FakeStreamxClientConfig("/fake/other-site/instance",
        Arrays.asList("/.*/other-site/.*", "/.*/dam/.*"));
  }

  private static FakeStreamxClientConfig getMySiteFakeStreamxClientConfig() {
    return new FakeStreamxClientConfig("/fake/my-site/instance",
        Arrays.asList("/.*/my-site/.*", "/.*/dam/.*"));
  }

  private static FakeStreamxClientConfig getDefaultFakeStreamxClientConfig() {
    return new FakeStreamxClientConfig("/fake/streamx/instance", Collections.singletonList(".*"));
  }
}
