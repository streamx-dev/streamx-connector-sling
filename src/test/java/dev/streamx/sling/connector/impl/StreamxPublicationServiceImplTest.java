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
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssumptions.given;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

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
  private IngestionTriggerJobExecutor ingestionTriggerJobExecutor;
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
  private void initializeComponentsIfNotInitialized() {
    if (publicationService != null) {
      return;
    }

    publicationService = new StreamxPublicationServiceImpl();

    JobExecutor publicationJobExecutor = new PublicationJobExecutor();

    for (FakeStreamxClientConfig config : fakeStreamxClientConfigs) {
      slingContext.registerService(StreamxClientConfig.class, config);
    }

    fakeStreamxClientFactory = new FakeStreamxClientFactory();
    slingContext.registerService(StreamxClientFactory.class, fakeStreamxClientFactory);

    fakeJobManager = spy(new FakeJobManager(Collections.singletonList(publicationJobExecutor)));

    doAnswer(invocationOnMock -> {
      // process the ingestion job immediately in tests
      Job job = new FakeJob(invocationOnMock.getArgument(0), invocationOnMock.getArgument(1));
      ingestionTriggerJobExecutor.process(job, new FakeJobExecutionContext());
      return job;
    }).when(fakeJobManager).addJob(eq(IngestionTriggerJobExecutor.JOB_TOPIC), anyMap());

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

    ingestionTriggerJobExecutor = slingContext.registerInjectActivateService(IngestionTriggerJobExecutor.class);

    fakeStreamxClient = fakeStreamxClientFactory.getFakeClient("/fake/streamx/instance");
  }

  @Test
  void shouldPublishSinglePage() {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathIsPublished("/content/my-site/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenPublicationsContainsExactly(
        publishedPage("/content/my-site/page-1.html")
    );
  }

  @Test
  void shouldPublishMultiplePages() {
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
  void shouldPublishAndUnpublishSinglePage() {
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
  void shouldUnpublishSinglePage() {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathIsUnpublished("/content/my-site/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenPublicationsContainsExactly(
        unpublishedPage("/content/my-site/page-1.html")
    );
  }

  @Test
  void shouldUnpublishMultiplePages() {
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
  void shouldUnpublishEvenIfResourceDoesNotExist() {
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
  void shouldNotPublishIfIsDisabled() {
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
  void shouldNotPublishIfResourceDoesNotExist() {
    givenPageHierarchy("/content/my-site/page-1");

    whenPathIsPublished("/content/my-site/page-1/non-existing-page");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldNotPublishIfNoPublishDataWasReturnedByHandler() {
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
  void shouldNotPublishIfAnyPathIsEmpty() {
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
  void shouldNotPublishIfNoPathsWereGiven() {
    givenPageHierarchy("/content/my-site/page-1");

    whenPathsArePublished();
    whenPathsAreUnpublished();
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(0);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldNotPublishIfNoHandlerCanHandle() {
    givenPageHierarchy("/var/my-site-copy/page-1");

    whenPathIsPublished("/var/my-site-copy/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(0);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldPublishDifferentTypesOfContent() {
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
  void shouldNotPublishIfResourceWasRemovedAfterPublication() {
    givenPageHierarchy("/content/my-site/page-1");

    whenPathIsPublished("/content/my-site/page-1");
    whenResourceIsRemoved("/content/my-site/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldCreatePublishJobForEachInstance() {
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
  void shouldPublishToStreamxInstanceIfPathMatchesPattern() {
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
  void shouldUpdateContentOnStreamxForRelatedResources() {
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
  void shouldUpdateRelatedResourcesJustOnceEvenIfWillBeReturnedByMultipleSelectors() {
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
  void shouldUpdateRelatedResourcesJustOnceEvenIfRelatesToMultiplePublishedResources() {
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
  void shouldNotSendExtraUpdateToRelatedResourcesIfItIsPublishedExplicitly() {
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
  void shouldInternallyHandleExceptionWhileAddingNewSlingJob() {
    initializeComponentsIfNotInitialized();
    doReturn(null).when(fakeJobManager).addJob(anyString(), anyMap());

    givenPageHierarchy("/content/my-site/page-1");

    whenPathIsPublished("/content/my-site/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(0);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldSubmitIngestionTriggerJobs() {
    // given
    initializeComponentsIfNotInitialized();
    doCallRealMethod().when(fakeJobManager).addJob(anyString(), anyMap());

    // when
    publicationService.publish(Collections.singletonList(new PageResourceInfo("path-1")));
    publicationService.unpublish(Collections.singletonList(new AssetResourceInfo("path-2")));

    // then
    List<FakeJob> queuedJobs = fakeJobManager.getJobQueue();
    assertThat(queuedJobs).hasSize(2);

    FakeJob publishJob = queuedJobs.get(0);
    assertThat(IngestionTriggerJobProperties.getAction(publishJob))
        .isEqualTo("PUBLISH");
    assertThat(IngestionTriggerJobProperties.getResources(publishJob))
        .containsExactly("{\"path\":\"path-1\",\"properties\":{\"jcr:primaryType\":\"cq:Page\"}}");

    FakeJob unpublishJob = queuedJobs.get(1);
    assertThat(IngestionTriggerJobProperties.getAction(unpublishJob))
        .isEqualTo("UNPUBLISH");
    assertThat(IngestionTriggerJobProperties.getResources(unpublishJob))
        .containsExactly("{\"path\":\"path-2\",\"properties\":{\"jcr:primaryType\":\"dam:Asset\"}}");
  }

  private void givenPageHierarchy(String... paths) {
    try {
      for (String path : paths) {
        slingContext.create().resource(path);
      }
      resourceResolver.commit();
    } catch (PersistenceException ex) {
      fail(ex.getMessage(), ex);
    }
  }

  private void givenAsset(String path) {
    try {
      slingContext.create().resource(path);
      resourceResolver.commit();
    } catch (PersistenceException ex) {
      fail(ex.getMessage(), ex);
    }
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

  private void whenPathIsPublished(String path) {
    initializeComponentsIfNotInitialized();
    publicationService.publish(toResourceInfoList(path));
  }

  private void whenPathsArePublished(String... paths) {
    initializeComponentsIfNotInitialized();
    publicationService.publish(toResourceInfoList(paths));
  }

  private void whenPathIsUnpublished(String path) {
    initializeComponentsIfNotInitialized();
    publicationService.unpublish(toResourceInfoList(path));
  }

  private void whenPathsAreUnpublished(String... paths) {
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

  private void whenResourceIsRemoved(String path) {
    Resource resource = resourceResolver.getResource(path);
    assertThat(resource).isNotNull();
    try {
      resourceResolver.delete(resource);
      resourceResolver.commit();
    } catch (PersistenceException ex) {
      fail(ex.getMessage(), ex);
    }
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
