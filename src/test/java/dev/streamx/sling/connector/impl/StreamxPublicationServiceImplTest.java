package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.*;
import dev.streamx.sling.connector.handlers.resourcepath.ResourcePathPublicationHandler;
import dev.streamx.sling.connector.handlers.resourcepath.ResourcePathPublicationHandlerConfig;
import dev.streamx.sling.connector.test.util.AssetResourceInfo;
import dev.streamx.sling.connector.test.util.PageResourceInfo;
import dev.streamx.sling.connector.test.util.ResourceResolverMocks;
import dev.streamx.sling.connector.testing.handlers.Asset;
import dev.streamx.sling.connector.testing.handlers.AssetPublicationHandler;
import dev.streamx.sling.connector.testing.handlers.ImpostorPublicationHandler;
import dev.streamx.sling.connector.testing.handlers.OtherPagePublicationHandler;
import dev.streamx.sling.connector.testing.handlers.PagePublicationHandler;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJob;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJobExecutionContext;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJobManager;
import dev.streamx.sling.connector.testing.streamx.clients.ingestion.FakeStreamxClient;
import dev.streamx.sling.connector.testing.streamx.clients.ingestion.Publication;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssumptions.given;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(SlingContextExtension.class)
class StreamxPublicationServiceImplTest {

  private static final String PAGES_CHANNEL = "pages";
  private static final String ASSETS_CHANNEL = "assets";
  private static final String RELATED_ASSET_TO_PUBLISH = "/content/dam/assets/my-site/related/image1.jpg";
  private static final String OTHER_RELATED_ASSET_TO_PUBLISH = "/content/dam/assets/my-site/related/script1.js";

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

  private final SlingRequestProcessor dummyRequestProcessor = (HttpServletRequest request, HttpServletResponse response, ResourceResolver resolver) -> {
    String requestUri = request.getRequestURI();
    String pageName = extractPageNameWithoutExtension(requestUri);
    response.setContentType("text/html");
    response.getWriter().write(pageName);
  };

  private final RelatedResourcesSelector relatedAssetsSelector = resource ->
       Arrays.asList(
          new AssetResourceInfo(RELATED_ASSET_TO_PUBLISH),
          new AssetResourceInfo(OTHER_RELATED_ASSET_TO_PUBLISH)
      );

  private final ResourcePathPublicationHandler<Asset> assetResourcePathPublicationHandler = new ResourcePathPublicationHandler<>(resourceResolverFactory, dummyRequestProcessor) {

    @Override
    public ResourcePathPublicationHandlerConfig configuration() {
      return new ResourcePathPublicationHandlerConfig() {
        @Override
        public String resourcePathRegex() {
          return "(" + RELATED_ASSET_TO_PUBLISH + "|" + OTHER_RELATED_ASSET_TO_PUBLISH + ")";
        }

        @Override
        public String channel() {
          return ASSETS_CHANNEL;
        }

        @Override
        public boolean isEnabled() {
          return true;
        }
      };
    }

    @Override
    public Class<Asset> modelClass() {
      return Asset.class;
    }

    @Override
    public Asset model(InputStream inputStream) {
      try {
        return new Asset(IOUtils.toByteArray(inputStream));
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    @Override
    public String getId() {
      return "assetResourcePathPublicationHandler";
    }
  };

  @BeforeEach
  void setUp() {
    handlers.add(new PagePublicationHandler(resourceResolver));
    handlers.add(new AssetPublicationHandler(resourceResolver));
    fakeStreamxClientConfigs.add(getDefaultFakeStreamxClientConfig());
  }

  @SuppressWarnings("ReturnOfNull")
  private void initializeComponentsIfNotInitialized() throws Exception {
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

    ResourceResolverMocks.configure(resourceResolver, resourceResolverFactory);

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
    when(job.getProperty(IngestionTriggerJobHelper.PN_STREAMX_INGESTION_RESOURCES, String[].class)).thenReturn(serializedResources);
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
        publishedPage("/content/my-site/page-1.html")
    );
  }

  @Test
  void shouldPublishMultiplePages() throws Exception {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathsArePublished("/content/my-site/page-1", "/content/my-site/page-1/page-2");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(2);
    thenPublicationsContainsExactly(
        publishedPage("/content/my-site/page-1.html"),
        publishedPage("/content/my-site/page-1/page-2.html")
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
        publishedPage("/content/my-site/page-1.html"),
        unpublishedPage("/content/my-site/page-1.html")
    );
  }

  @Test
  void shouldUnpublishSinglePage() throws Exception {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathIsUnpublished("/content/my-site/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenPublicationsContainsExactly(
        unpublishedPage("/content/my-site/page-1.html")
    );
  }

  @Test
  void shouldUnpublishMultiplePages() throws Exception {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathsAreUnpublished("/content/my-site/page-1", "/content/my-site/page-1/page-2");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(2);
    thenPublicationsContainsExactly(
        unpublishedPage("/content/my-site/page-1.html"),
        unpublishedPage("/content/my-site/page-1/page-2.html")
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
        unpublishedPage("/content/my-site/page-1.html"),
        unpublishedAsset("/content/dam/asset-1.jpeg")
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
        publishedPage("/content/my-site/page-1.html"),
        unpublishedPage("/content/my-site/page-1.html")
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
        publishedPage("/content/my-site/page-1.html"),
        publishedAsset("/content/dam/asset-1.jpeg"),
        publishedPage("/content/my-site/page-1/page-2.html")
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
        publishedPage("/content/my-site/page-1.html"),
        publishedPage("/content/other-site/page-1.html"),
        publishedAsset("/content/dam/asset-1.jpeg")
    );

    thenInstancePublicationsContainsExactly(
        "/fake/my-site/instance",
        publishedPage("/content/my-site/page-1.html"),
        publishedAsset("/content/dam/asset-1.jpeg")
    );

    thenInstancePublicationsContainsExactly(
        "/fake/other-site/instance",
        publishedPage("/content/other-site/page-1.html"),
        publishedAsset("/content/dam/asset-1.jpeg")
    );
  }

  @Test
  void shouldUpdateContentOnStreamxForRelatedResources() throws Exception {
    givenPageHierarchy(
        "/content/my-site/page-1",
        RELATED_ASSET_TO_PUBLISH,
        OTHER_RELATED_ASSET_TO_PUBLISH
    );

    givenRelatedResourcesSelectors(relatedAssetsSelector);

    whenPathsArePublished(
        "/content/my-site/page-1"
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(3);

    thenPublicationsContainsExactly(
        publishedPage("/content/my-site/page-1.html"),
        publishedAsset(RELATED_ASSET_TO_PUBLISH),
        publishedAsset(OTHER_RELATED_ASSET_TO_PUBLISH)
    );
  }

  @Test
  void shouldUpdateRelatedResourcesJustOnceEvenIfWillBeReturnedByMultipleSelectors() throws Exception {
    givenPageHierarchy(
        "/content/my-site/page-1",
        RELATED_ASSET_TO_PUBLISH,
        OTHER_RELATED_ASSET_TO_PUBLISH
    );

    givenRelatedResourcesSelectors(relatedAssetsSelector, relatedAssetsSelector, relatedAssetsSelector);

    whenPathsArePublished(
        "/content/my-site/page-1"
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(3);

    thenPublicationsContainsExactly(
        publishedPage("/content/my-site/page-1.html"),
        publishedAsset(RELATED_ASSET_TO_PUBLISH),
        publishedAsset(OTHER_RELATED_ASSET_TO_PUBLISH)
    );
  }

  @Test
  void shouldUpdateRelatedResourcesJustOnceEvenIfRelatesToMultiplePublishedResources() throws Exception {
    givenPageHierarchy(
        "/content/my-site/page-1",
        "/content/my-site/page-2",
        RELATED_ASSET_TO_PUBLISH,
        OTHER_RELATED_ASSET_TO_PUBLISH
    );

    givenRelatedResourcesSelectors(relatedAssetsSelector);

    whenPathsArePublished(
        "/content/my-site/page-1",
        "/content/my-site/page-2"
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(4);

    thenPublicationsContainsExactly(
        publishedPage("/content/my-site/page-1.html"),
        publishedPage("/content/my-site/page-2.html"),
        publishedAsset(RELATED_ASSET_TO_PUBLISH),
        publishedAsset(OTHER_RELATED_ASSET_TO_PUBLISH)
    );
  }

  @Test
  void shouldNotSendExtraUpdateToRelatedResourcesIfItIsPublishedExplicitly() throws Exception {
    givenPageHierarchy(
        "/content/my-site/page-1",
        RELATED_ASSET_TO_PUBLISH,
        OTHER_RELATED_ASSET_TO_PUBLISH
    );

    givenRelatedResourcesSelectors(relatedAssetsSelector);

    whenPathsArePublished(
        "/content/my-site/page-1",
        RELATED_ASSET_TO_PUBLISH
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(3);

    thenPublicationsContainsExactly(
        publishedPage("/content/my-site/page-1.html"),
        publishedAsset(RELATED_ASSET_TO_PUBLISH),
        publishedAsset(OTHER_RELATED_ASSET_TO_PUBLISH)
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
    publicationService.publish(Collections.singletonList(new PageResourceInfo("path-1")));
    publicationService.unpublish(Collections.singletonList(new AssetResourceInfo("path-2")));

    // then
    List<FakeJob> queuedJobs = fakeJobManager.getJobQueue();
    assertThat(queuedJobs).hasSize(2);

    FakeJob publishJob = queuedJobs.get(0);
    assertThat(publishJob.getProperty(IngestionTriggerJobHelper.PN_STREAMX_INGESTION_ACTION, String.class))
        .isEqualTo("PUBLISH");
    assertThat(publishJob.getProperty(IngestionTriggerJobHelper.PN_STREAMX_INGESTION_RESOURCES, String[].class))
        .containsExactly("{\"path\":\"path-1\",\"properties\":{\"jcr:primaryType\":\"cq:Page\"}}");

    FakeJob unpublishJob = queuedJobs.get(1);
    assertThat(unpublishJob.getProperty(IngestionTriggerJobHelper.PN_STREAMX_INGESTION_ACTION, String.class))
        .isEqualTo("UNPUBLISH");
    assertThat(unpublishJob.getProperty(IngestionTriggerJobHelper.PN_STREAMX_INGESTION_RESOURCES, String[].class))
        .containsExactly("{\"path\":\"path-2\",\"properties\":{\"jcr:primaryType\":\"dam:Asset\"}}");
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
    this.handlers.add(assetResourcePathPublicationHandler);
  }

  private void givenHandlers(PublicationHandler<?>... handlers) {
    this.handlers.clear();
    this.handlers.addAll(Arrays.asList(handlers));
  }

  private void givenStreamxClientInstances(FakeStreamxClientConfig... configs) {
    this.fakeStreamxClientConfigs.clear();
    this.fakeStreamxClientConfigs.addAll(Arrays.asList(configs));
  }

  private void whenPathIsPublished(String path) throws Exception {
    initializeComponentsIfNotInitialized();
    publicationService.publish(toResourceInfoList(path));
  }

  private void whenPathsArePublished(String... paths) throws Exception {
    initializeComponentsIfNotInitialized();
    publicationService.publish(toResourceInfoList(paths));
  }

  private void whenPathIsUnpublished(String path) throws Exception {
    initializeComponentsIfNotInitialized();
    publicationService.unpublish(toResourceInfoList(path));
  }

  private void whenPathsAreUnpublished(String... paths) throws Exception {
    initializeComponentsIfNotInitialized();
    publicationService.unpublish(toResourceInfoList(paths));
  }

  private static List<ResourceInfo> toResourceInfoList(String... paths) {
    return Arrays.stream(paths).map(path ->
        StringUtils.contains(path, "/dam/")
            ? new AssetResourceInfo(path)
            : new PageResourceInfo(path)
    ).collect(Collectors.toList());
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

  private void thenPublicationsContainsExactly(Publication... publications) {
    assertThat(fakeStreamxClient.getPublications())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(publications);
  }

  private void thenInstancePublicationsContainsExactly(String streamxInstanceUrl, Publication... publications) {
    FakeStreamxClient streamxClient = fakeStreamxClientFactory.getFakeClient(streamxInstanceUrl);
    assertThat(streamxClient.getPublications())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(publications);
  }

  private void thenNoPublicationsWereMade() {
    assertThat(fakeStreamxClient.getPublications()).isEmpty();
  }

  private Publication publishedPage(String key) {
    return publishedResource(key, PAGES_CHANNEL, "Page: ");
  }

  private Publication publishedAsset(String key) {
    return publishedResource(key, ASSETS_CHANNEL, "Asset: ");
  }

  private Publication publishedResource(String key, String channel, String dataPrefix) {
    String data = dataPrefix + extractPageNameWithoutExtension(key);
    return new Publication(PublicationAction.PUBLISH, key, channel, data);
  }

  private static String extractPageNameWithoutExtension(String pagePath) {
    String pageName = StringUtils.substringAfterLast(pagePath, "/");
    return StringUtils.removeEnd(pageName, ".html");
  }

  private Publication unpublishedPage(String key) {
    return unpublishedResource(key, PAGES_CHANNEL);
  }

  private Publication unpublishedAsset(String key) {
    return unpublishedResource(key, ASSETS_CHANNEL);
  }

  private Publication unpublishedResource(String key, String channel) {
    return new Publication(PublicationAction.UNPUBLISH, key, channel, null);
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
