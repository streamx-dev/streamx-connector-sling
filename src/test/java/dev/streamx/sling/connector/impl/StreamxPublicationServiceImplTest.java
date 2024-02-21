package dev.streamx.sling.connector.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.BDDAssertions.then;

import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.StreamxPublicationService;
import dev.streamx.sling.connector.testing.handlers.AssetPublicationHandler;
import dev.streamx.sling.connector.testing.handlers.ImpostorPublicationHandler;
import dev.streamx.sling.connector.testing.handlers.PagePublicationHandler;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJobManager;
import dev.streamx.sling.connector.testing.streamx.clients.ingestion.FakeStreamxClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlingContextExtension.class)
class StreamxPublicationServiceImplTest {

  private final SlingContext slingContext = new SlingContext();
  private final Map<String, Object> publicationServiceConfig = new HashMap<>();
  private final List<PublicationHandler<?>> handlers = new ArrayList<>();

  private StreamxPublicationService publicationService;
  private FakeJobManager fakeJobManager;
  private FakeStreamxClient fakeStreamxClient;

  @BeforeEach
  void setUp() {
    handlers.add(new PagePublicationHandler());
    handlers.add(new AssetPublicationHandler());
  }

  private void initializeComponentsIfNotInitialized() {
    if (publicationService != null) {
      return;
    }

    StreamxPublicationServiceImpl publicationServiceImpl = new StreamxPublicationServiceImpl();
    FakeStreamxClientFactory fakeStreamxClientProvider = new FakeStreamxClientFactory();
    slingContext.registerService(StreamxClientFactory.class, fakeStreamxClientProvider);
    fakeJobManager = new FakeJobManager(Collections.singletonList(publicationServiceImpl));
    slingContext.registerService(JobManager.class, fakeJobManager);
    for (PublicationHandler<?> handler : handlers) {
      slingContext.registerService(PublicationHandler.class, handler);
    }
    slingContext.registerInjectActivateService(new PublicationHandlerRegistry());

    slingContext.registerInjectActivateService(publicationServiceImpl, publicationServiceConfig);

    publicationService = publicationServiceImpl;
    fakeStreamxClient = fakeStreamxClientProvider.getFakeClient();
  }

  @Test
  void shouldPublishSinglePage() throws PersistenceException {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathIsPublished("/content/my-site/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages-inbox", "Page: page-1")
    );
  }

  @Test
  void shouldPublishMultiplePages() throws PersistenceException {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathsArePublished("/content/my-site/page-1", "/content/my-site/page-1/page-2");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(2);
    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages-inbox", "Page: page-1"),
        publish("/content/my-site/page-1/page-2.html", "pages-inbox", "Page: page-2")
    );
  }

  @Test
  void shouldUnpublishSinglePage() throws PersistenceException {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathIsUnpublished("/content/my-site/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenPublicationsContainsExactly(
        unpublish("/content/my-site/page-1.html")
    );
  }

  @Test
  void shouldUnpublishMultiplePages() throws PersistenceException {
    givenPageHierarchy("/content/my-site/page-1/page-2/page-3");

    whenPathsAreUnpublished("/content/my-site/page-1", "/content/my-site/page-1/page-2");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(2);
    thenPublicationsContainsExactly(
        unpublish("/content/my-site/page-1.html"),
        unpublish("/content/my-site/page-1/page-2.html")
    );
  }

  @Test
  void shouldNotPublishIfIsDisabled() throws PersistenceException {
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
  void shouldNotPublishIfResourceDoesNotExist() throws PersistenceException {
    givenPageHierarchy("/content/my-site/page-1");

    whenPathIsPublished("/content/my-site/page-1/non-existing-page");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldNotPublishIfNoPublishDataWasReturnedByHandler() throws PersistenceException {
    givenPageHierarchy("/content/my-site/page-1");
    givenPageHierarchy("/content/impostor-site/page-2");
    givenHandlers(
        new PagePublicationHandler(),
        new ImpostorPublicationHandler()
    );

    whenPathsArePublished("/content/my-site/page-1", "/content/impostor-site/page-2");
    whenPathsAreUnpublished("/content/my-site/page-1", "/content/impostor-site/page-2");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(4);
    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages-inbox", "Page: page-1"),
        unpublish("/content/my-site/page-1.html")
    );
  }

  @Test
  void shouldNotPublishIfPathsAreEmpty() throws PersistenceException {
    givenPageHierarchy("/content/my-site/page-1");

    whenPathsArePublished("", "/content/my-site/page-1", null);
    whenPathsAreUnpublished(null, "/content/my-site/page-1", "");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(2);
    thenPublicationsContainsExactly(
        publish("/content/my-site/page-1.html", "pages-inbox", "Page: page-1"),
        unpublish("/content/my-site/page-1.html")
    );
  }

  @Test
  void shouldNotPublishIfNoPathsWereGiven() throws PersistenceException {
    givenPageHierarchy("/content/my-site/page-1");

    whenPathsArePublished();
    whenPathsAreUnpublished();
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(0);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldNotPublishIfNoHandlerCanHandle() throws PersistenceException {
    givenPageHierarchy("/var/my-site-copy/page-1");

    whenPathIsPublished("/var/my-site-copy/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(0);
    thenNoPublicationsWereMade();
  }

  @Test
  void shouldPublishDifferentTypesOfContent() throws PersistenceException {
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
        publish("/content/my-site/page-1.html", "pages-inbox", "Page: page-1"),
        publish("/content/dam/asset-1.jpeg", "assets-inbox", "Asset: asset-1.jpeg"),
        publish("/content/my-site/page-1/page-2.html", "pages-inbox", "Page: page-2")
    );
  }

  @Test
  void shouldNotPublishIfResourceWasRemovedAfterPublication() throws PersistenceException {
    givenPageHierarchy("/content/my-site/page-1");

    whenPathIsPublished("/content/my-site/page-1");
    whenResourceIsRemoved("/content/my-site/page-1");
    whenAllJobsAreProcessed();

    thenProcessedJobsCountIs(1);
    thenNoPublicationsWereMade();
  }

  private void givenPageHierarchy(String path) throws PersistenceException {
    slingContext.create().resource(path);
    slingContext.resourceResolver().commit();
  }

  private void givenAsset(String path) throws PersistenceException {
    slingContext.create().resource(path);
    slingContext.resourceResolver().commit();
  }

  private void givenPublicationService(Consumer<Map<String, Object>> propertiesModifier) {
    propertiesModifier.accept(publicationServiceConfig);
  }

  private void givenHandlers(PublicationHandler<?>... handlers) {
    this.handlers.clear();
    this.handlers.addAll(Arrays.asList(handlers));
  }

  private void whenPathIsPublished(String path) {
    initializeComponentsIfNotInitialized();
    publicationService.publish(Collections.singletonList(path));
  }

  private void whenPathsArePublished(String... path) {
    initializeComponentsIfNotInitialized();
    publicationService.publish(Arrays.asList(path));
  }

  private void whenPathIsUnpublished(String path) {
    initializeComponentsIfNotInitialized();
    publicationService.unpublish(Collections.singletonList(path));
  }

  private void whenPathsAreUnpublished(String... path) {
    initializeComponentsIfNotInitialized();
    publicationService.unpublish(Arrays.asList(path));
  }

  private void whenAllJobsAreProcessed() {
    fakeJobManager.processAllJobs();
  }

  private void whenResourceIsRemoved(String path) throws PersistenceException {
    ResourceResolver resourceResolver = slingContext.resourceResolver();
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

  private void thenNoPublicationsWereMade() {
    assertThat(fakeStreamxClient.getPublications()).isEmpty();
  }

  private Tuple publish(String key, String channel, String data) {
    return tuple("Publish", key, channel, data);
  }

  private Tuple unpublish(String key) {
    return tuple("Unpublish", key, null, null);
  }
}
