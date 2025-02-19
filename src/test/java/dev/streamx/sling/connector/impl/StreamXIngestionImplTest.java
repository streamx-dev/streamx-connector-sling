package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.*;
import dev.streamx.sling.connector.testing.handlers.AssetIngestionDataFactory;
import dev.streamx.sling.connector.testing.handlers.ImpostorIngestionDataFactory;
import dev.streamx.sling.connector.testing.handlers.OtherPageIngestionDataFactory;
import dev.streamx.sling.connector.testing.handlers.PageIngestionDataFactory;
import dev.streamx.sling.connector.testing.selectors.RelatedPagesSelector;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJobManager;
import dev.streamx.sling.connector.testing.streamx.clients.ingestion.FakeStreamxClient;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
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

@ExtendWith(SlingContextExtension.class)
class StreamXIngestionImplTest {

  private final SlingContext slingContext = new SlingContext();
  private final ResourceResolver resourceResolver = slingContext.resourceResolver();
  private final Map<String, Object> publicationServiceConfig = new HashMap<>();
  private final List<IngestionDataFactory<?>> handlers = new ArrayList<>();
  private final List<RelatedResourcesSelector> relatedResourcesSelectors = new ArrayList<>();
  private final List<FakeStreamxClientConfig> fakeStreamxClientConfigs = new ArrayList<>();

  private StreamXIngestion publicationService;
  private FakeJobManager fakeJobManager;
  private FakeStreamxClient fakeStreamxClient;
  private FakeStreamxClientFactory fakeStreamxClientFactory;

  @BeforeEach
  void setUp() {
    handlers.add(new PageIngestionDataFactory(resourceResolver));
    handlers.add(new AssetIngestionDataFactory(resourceResolver));
    fakeStreamxClientConfigs.add(getDefaultFakeStreamxClientConfig());
  }

  private void initializeComponentsIfNotInitialized() {
    if (publicationService != null) {
      return;
    }

    StreamXIngestionImpl publicationServiceImpl = new StreamXIngestionImpl();
    JobExecutor publicationJobExecutor = new PublicationJobExecutor();

    for (FakeStreamxClientConfig config : fakeStreamxClientConfigs) {
      slingContext.registerService(StreamxClientConfig.class, config);
    }

    fakeStreamxClientFactory = new FakeStreamxClientFactory();
    slingContext.registerService(StreamxClientFactory.class, fakeStreamxClientFactory);
    fakeJobManager = new FakeJobManager(Collections.singletonList(publicationJobExecutor));
    slingContext.registerService(JobManager.class, fakeJobManager);
    slingContext.registerService(PublicationRetryPolicy.class, new DefaultPublicationRetryPolicy());
    for (IngestionDataFactory<?> handler : handlers) {
      slingContext.registerService(IngestionDataFactory.class, handler);
    }
    for (RelatedResourcesSelector selector : relatedResourcesSelectors) {
      slingContext.registerService(RelatedResourcesSelector.class, selector);
    }
    slingContext.registerInjectActivateService(StreamxClientStoreImpl.class);
    slingContext.registerInjectActivateService(new PublicationHandlerRegistry());

    slingContext.registerInjectActivateService(new RelatedResourcesSelectorRegistry());

    slingContext.registerInjectActivateService(publicationServiceImpl, publicationServiceConfig);
    slingContext.registerInjectActivateService(publicationJobExecutor);

    publicationService = publicationServiceImpl;
    fakeStreamxClient = fakeStreamxClientFactory.getFakeClient("/fake/streamx/instance");
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
        new PageIngestionDataFactory(resourceResolver),
        new ImpostorIngestionDataFactory()
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
        new PageIngestionDataFactory(resourceResolver),
        new OtherPageIngestionDataFactory(resourceResolver),
        new AssetIngestionDataFactory(resourceResolver)
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
    givenPageHierarchy("/content/my-site/related-page-to-unpublish");

    givenRelatedResourcesSelectors(new RelatedPagesSelector());

    whenPathsArePublished(
        "/content/my-site/page-1"
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(3);

    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages", "Page: page-1"),
        publish("/content/my-site/related-page-to-publish.html", "pages", "Page: related-page-to-publish"),
        unpublish("/content/my-site/related-page-to-unpublish.html", "pages")
    );
  }

  @Test
  void shouldUpdateRelatedResourcesJustOnceEvenIfWillBeReturnedByMultipleSelectors()
      throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1");
    givenPageHierarchy("/content/my-site/related-page-to-publish");
    givenPageHierarchy("/content/my-site/related-page-to-unpublish");

    givenRelatedResourcesSelectors(new RelatedPagesSelector(), new RelatedPagesSelector(), new RelatedPagesSelector());

    whenPathsArePublished(
        "/content/my-site/page-1"
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(3);

    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages", "Page: page-1"),
        publish("/content/my-site/related-page-to-publish.html", "pages", "Page: related-page-to-publish"),
        unpublish("/content/my-site/related-page-to-unpublish.html", "pages")
    );
  }

  @Test
  void shouldUpdateRelatedResourcesJustOnceEvenIfRelatesToMultiplePublishedResources()
      throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1");
    givenPageHierarchy("/content/my-site/page-2");
    givenPageHierarchy("/content/my-site/related-page-to-publish");
    givenPageHierarchy("/content/my-site/related-page-to-unpublish");

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
        publish("/content/my-site/related-page-to-publish.html", "pages", "Page: related-page-to-publish"),
        unpublish("/content/my-site/related-page-to-unpublish.html", "pages")
    );
  }

  @Test
  void shouldNotSendExtraUpdateToRelatedResourcesIfItIsPublishedExplicitly()
      throws PersistenceException, StreamxPublicationException {
    givenPageHierarchy("/content/my-site/page-1");
    givenPageHierarchy("/content/my-site/related-page-to-publish");
    givenPageHierarchy("/content/my-site/related-page-to-unpublish");

    givenRelatedResourcesSelectors(new RelatedPagesSelector());

    whenPathsArePublished(
        "/content/my-site/page-1",
        "/content/my-site/related-page-to-publish"
    );

    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(3);

    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages", "Page: page-1"),
        publish("/content/my-site/related-page-to-publish.html", "pages", "Page: related-page-to-publish"),
        unpublish("/content/my-site/related-page-to-unpublish.html", "pages")
    );
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

  private void givenHandlers(IngestionDataFactory<?>... handlers) {
    this.handlers.clear();
    this.handlers.addAll(Arrays.asList(handlers));
  }

  private void givenStreamxClientInstances(FakeStreamxClientConfig... configs) {
    this.fakeStreamxClientConfigs.clear();
    this.fakeStreamxClientConfigs.addAll(Arrays.asList(configs));
  }

  private void whenPathIsPublished(String path) throws StreamxPublicationException {
    initializeComponentsIfNotInitialized();
    publicationService.publish(Collections.singletonList(path));
  }

  private void whenPathsArePublished(String... path) throws StreamxPublicationException {
    initializeComponentsIfNotInitialized();
    publicationService.publish(Arrays.asList(path));
  }

  private void whenPathIsUnpublished(String path) throws StreamxPublicationException {
    initializeComponentsIfNotInitialized();
    publicationService.unpublish(Collections.singletonList(path));
  }

  private void whenPathsAreUnpublished(String... path) throws StreamxPublicationException {
    initializeComponentsIfNotInitialized();
    publicationService.unpublish(Arrays.asList(path));
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
