package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.IngestionTriggerJobHelper.PN_STREAMX_INGESTION_ACTION;
import static dev.streamx.sling.connector.impl.IngestionTriggerJobHelper.PN_STREAMX_RESOURCES_INFO;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_ACTION;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.selectors.content.ResourceContentRelatedResourcesSelector;
import dev.streamx.sling.connector.selectors.content.ResourceContentRelatedResourcesSelectorConfig;
import dev.streamx.sling.connector.test.util.PageResourceInfo;
import dev.streamx.sling.connector.test.util.ResourceMocks;
import dev.streamx.sling.connector.testing.handlers.AssetPublicationHandler;
import dev.streamx.sling.connector.testing.handlers.PagePublicationHandler;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJobManager;
import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionContext.ResultBuilder;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;

@ExtendWith(SlingContextExtension.class)
class StreamxPublicationServiceImplRelatedResourcesIngestionTest {

  private static final String IMAGE_1 = "/content/firsthops/us/en/image-1.jpg";
  private static final String IMAGE_2 = "/content/firsthops/us/en/image-2.jpg";
  private static final String IMAGE_3 = "/content/firsthops/us/en/image-3.jpg";

  private static final String REFERENCED_PAGE_1 = "/content/my-site/us/en/referenced-page-1.html";
  private static final String REFERENCED_PAGE_2 = "/content/my-site/us/en/referenced-page-2.html";
  private static final String REFERENCED_PAGE_3 = "/content/my-site/us/en/referenced-page-3.html";

  private static final String PAGE_JSON_RESOURCE_FILE_PATH = "src/test/resources/page.json";

  private final SlingContext slingContext = new SlingContext(ResourceResolverType.JCR_OAK);
  private final ResourceResolver resourceResolver = spy(slingContext.resourceResolver());
  private final ResourceResolverFactory resourceResolverFactory = mock(ResourceResolverFactory.class);
  private final FakeJobManager jobManager = new FakeJobManager(Collections.emptyList());
  private final StreamxPublicationServiceImpl publicationService = new StreamxPublicationServiceImpl();
  private final Job job = mock(Job.class);
  private final JobExecutionContext jobExecutionContext = mock(JobExecutionContext.class);

  // resource path + content
  private final Map<String, String> allTestPages = new LinkedHashMap<>();

  private final String PAGE_WITH_IMAGES_1_2_3 = registerPage(
      "/content/my-site/us/en/page-with-images-1-2-3.html",
      generateHtmlPageContent(IMAGE_1, IMAGE_2, IMAGE_3)
  );

  private final String PAGE_WITH_IMAGES_1_AND_3 = registerPage(
      "/content/my-site/us/en/page-with-images-1-and-3.html",
      generateHtmlPageContent(IMAGE_1, IMAGE_3)
  );

  private final String PAGE_WITH_REFERENCED_PAGES_1_2_3 = registerPage(
      "/content/my-site/us/en/page-with-referenced-pages-1-2-3.html",
      generateHtmlPageContent(REFERENCED_PAGE_1, REFERENCED_PAGE_2, REFERENCED_PAGE_3)
  );

  private final String PAGE_WITH_REFERENCED_PAGES_1_AND_3 = registerPage(
      "/content/my-site/us/en/page-with-referenced-pages-1-and-3.html",
      generateHtmlPageContent(REFERENCED_PAGE_1, REFERENCED_PAGE_3)
  );

  private final AtomicBoolean simulateResourcesAreEditedOnEachRead = new AtomicBoolean(false);

  private final SlingRequestProcessor requestProcessor = (HttpServletRequest request, HttpServletResponse response, ResourceResolver resourceResolver) -> {
    String requestURI = request.getRequestURI();

    if (requestURI.endsWith(".jpg")) {
      response.setContentType("application/octet-stream");
      response.getWriter().write("Content of image " + requestURI);
    } else {
      response.setContentType("text/html");
      response.getWriter().write(allTestPages.get(requestURI));
    }

    if (simulateResourcesAreEditedOnEachRead.get()) {
      response.getWriter().write("edited at " + System.nanoTime());
    }
  };

  private final ResourceContentRelatedResourcesSelectorConfig relatedResourcesConfig = new ResourceContentRelatedResourcesSelectorConfig() {

    @Override
    public Class<? extends Annotation> annotationType() {
      return ResourceContentRelatedResourcesSelectorConfig.class;
    }

    @Override
    public String[] references_search$_$regexes() {
      return new String[]{
          "(/content/.+?\\.(jpg|html))",
      };
    }

    @Override
    public String references_exclude$_$from$_$result_regex() {
      return "";
    }

    @Override
    public String resource$_$path_postfix$_$to$_$append() {
      return "";
    }

    @Override
    public String resource_required$_$path_regex() {
      return ".*";
    }

    @Override
    public String resource_required$_$primary$_$node$_$type_regex() {
      return ".*";
    }
  };

  private long publicationServiceProcessingTotalTimeMillis = 0;

  @BeforeEach
  void setup() throws Exception {
    configureResources();
    configureStreamxClient();
    configureServices();
  }

  @SuppressWarnings("deprecation")
  private void configureResources() throws Exception {
    doReturn(ResourceMocks.createAssetResourceMock())
        .when(resourceResolver)
        .resolve(ArgumentMatchers.<String>argThat(path -> path.endsWith(".jpg")));

    doReturn(ResourceMocks.createPageResourceMock())
        .when(resourceResolver)
        .resolve(ArgumentMatchers.<String>argThat(path -> path.endsWith(".html")));

    doReturn(resourceResolver).when(resourceResolverFactory).getAdministrativeResourceResolver(null);
    doNothing().when(resourceResolver).close();
  }

  private void configureServices() {
    slingContext.registerService(SlingRequestProcessor.class, requestProcessor);
    var selector = new ResourceContentRelatedResourcesSelector(relatedResourcesConfig, requestProcessor, resourceResolverFactory);
    slingContext.registerService(RelatedResourcesSelector.class, selector);
    slingContext.registerInjectActivateService(new RelatedResourcesSelectorRegistry());

    slingContext.registerService(PublicationHandler.class, new PagePublicationHandler(resourceResolver));
    slingContext.registerService(PublicationHandler.class, new AssetPublicationHandler(resourceResolver));
    slingContext.registerInjectActivateService(new PublicationHandlerRegistry());

    doReturn(mock(ResultBuilder.class)).when(jobExecutionContext).result();
    slingContext.registerService(JobManager.class, jobManager);

    slingContext.registerInjectActivateService(publicationService);
  }

  private void configureStreamxClient() {
    slingContext.registerService(StreamxClientConfig.class, new FakeStreamxClientConfig("any", Collections.emptyList()));
    slingContext.registerService(StreamxClientFactory.class, new FakeStreamxClientFactory());

    StreamxInstanceClient streamxClientMock = mock(StreamxInstanceClient.class);
    doReturn("streamxClient").when(streamxClientMock).getName();

    StreamxClientStore streamxClientStore = mock(StreamxClientStoreImpl.class);
    doReturn(List.of(streamxClientMock)).when(streamxClientStore).getForResource(anyString());
    slingContext.registerInjectActivateService(streamxClientStore);
  }

  @Test
  void shouldUnpublishRelatedResources_WhenUnpublishingParentPage_IfNoMoreReferences_WhenResourcesAreNeverEdited() {
    simulateResourcesAreEditedOnEachRead.set(false);
    shouldUnpublishRelatedResources_WhenUnpublishingParentPage_IfNoMoreReferences();
  }

  @Test
  void shouldUnpublishRelatedResources_WhenUnpublishingParentPage_IfNoMoreReferences_WhenResourcesAreEditedBetweenEachRequest() {
    simulateResourcesAreEditedOnEachRead.set(true);
    shouldUnpublishRelatedResources_WhenUnpublishingParentPage_IfNoMoreReferences();
  }

  private void shouldUnpublishRelatedResources_WhenUnpublishingParentPage_IfNoMoreReferences() {
    // given
    boolean areResourcesEditedOnEachRead = simulateResourcesAreEditedOnEachRead.get();

    // when 1: publish page that contains image 1, image 2 and image 3
    publishPage(PAGE_WITH_IMAGES_1_2_3);

    // then
    assertPublishedTimes(IMAGE_1, 1);
    assertPublishedTimes(IMAGE_2, 1);
    assertPublishedTimes(IMAGE_3, 1);
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_IMAGES_1_2_3, IMAGE_1, IMAGE_2, IMAGE_3);

    // when 2: publish page that contains image 1 and image 3
    publishPage(PAGE_WITH_IMAGES_1_AND_3);

    // then
    assertPublishedTimes(IMAGE_1, areResourcesEditedOnEachRead ? 2 : 1);
    assertPublishedTimes(IMAGE_2, 1);
    assertPublishedTimes(IMAGE_3, areResourcesEditedOnEachRead ? 2 : 1);
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_IMAGES_1_2_3, PAGE_WITH_IMAGES_1_AND_3, IMAGE_1, IMAGE_2, IMAGE_3);

    // when 3: unpublish first page
    unpublishPage(PAGE_WITH_IMAGES_1_2_3);

    // then: expect image 2 to be gone, as it doesn't have any references anymore
    assertPublishedTimes(IMAGE_1, areResourcesEditedOnEachRead ? 2 : 1);
    assertPublishedTimes(IMAGE_2, 1);
    assertPublishedTimes(IMAGE_3, areResourcesEditedOnEachRead ? 2 : 1);
    assertUnpublishedTimes(IMAGE_1, 0);
    assertUnpublishedTimes(IMAGE_2, 1);
    assertUnpublishedTimes(IMAGE_3, 0);
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_IMAGES_1_AND_3, IMAGE_1, IMAGE_3);

    // when 4: unpublish second page
    unpublishPage(PAGE_WITH_IMAGES_1_AND_3);

    // then: expect images to be gone
    assertPublishedTimes(IMAGE_1, areResourcesEditedOnEachRead ? 2 : 1);
    assertPublishedTimes(IMAGE_2, 1);
    assertPublishedTimes(IMAGE_3, areResourcesEditedOnEachRead ? 2 : 1);
    assertUnpublishedTimes(IMAGE_1, 1);
    assertUnpublishedTimes(IMAGE_2, 1);
    assertUnpublishedTimes(IMAGE_3, 1);
    assertResourcesCurrentlyOnStreamX();

    // when 5: publish first page again
    publishPage(PAGE_WITH_IMAGES_1_2_3);

    // then
    assertPublishedTimes(IMAGE_1, areResourcesEditedOnEachRead ? 3 : 2);
    assertPublishedTimes(IMAGE_2, 2);
    assertPublishedTimes(IMAGE_3, areResourcesEditedOnEachRead ? 3 : 2);
    assertUnpublishedTimes(IMAGE_1, 1);
    assertUnpublishedTimes(IMAGE_2, 1);
    assertUnpublishedTimes(IMAGE_3, 1);
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_IMAGES_1_2_3, IMAGE_1, IMAGE_2, IMAGE_3);

    // when 6: unpublish first page again
    unpublishPage(PAGE_WITH_IMAGES_1_2_3);

    // then: expect images to be gone
    assertPublishedTimes(IMAGE_1, areResourcesEditedOnEachRead ? 3 : 2);
    assertPublishedTimes(IMAGE_2, 2);
    assertPublishedTimes(IMAGE_3, areResourcesEditedOnEachRead ? 3 : 2);
    assertUnpublishedTimes(IMAGE_1, 2);
    assertUnpublishedTimes(IMAGE_2, 2);
    assertUnpublishedTimes(IMAGE_3, 2);
    assertResourcesCurrentlyOnStreamX();
  }

  @Test
  void shouldUnpublishUnreferencedImageWhenPublishingEditedPage() {
    // given
    unregisterAllPages();
    String page1Path = "/content/my-site/page-1.html";
    String page2Path = "/content/my-site/page-2.html";

    // when
    registerPage(page1Path, generateHtmlPageContent(IMAGE_1, IMAGE_2, IMAGE_3));
    registerPage(page2Path, generateHtmlPageContent(IMAGE_2, IMAGE_3));
    publishPage(page1Path);
    publishPage(page2Path);

    // then
    assertResourcesCurrentlyOnStreamX(page1Path, page2Path, IMAGE_1, IMAGE_2, IMAGE_3);
    // and
    verifyPublishedResourcesDataIsStored(page1Path, IMAGE_1, IMAGE_2, IMAGE_3);
    verifyPublishedResourcesDataIsStored(page2Path, IMAGE_2, IMAGE_3);

    // when: user edits page 1 to remove images 1 and 2 (leave only image 3 in it), and republishes the page
    editPage(page1Path, generateHtmlPageContent(IMAGE_3));
    publishPage(page1Path);

    // then: expecting image 1 to be automatically unpublished, since no other published page references it (page 2 is still referencing image 2)
    assertResourcesCurrentlyOnStreamX(page1Path, page2Path, IMAGE_2, IMAGE_3);
    // and
    verifyPublishedResourcesDataIsStored(page1Path, IMAGE_3);
    verifyPublishedResourcesDataIsStored(page2Path, IMAGE_2, IMAGE_3);
  }

  @Test
  void shouldNotUnpublishImage_WhileOtherPageReferencesIt() {
    // when
    publishPage(PAGE_WITH_IMAGES_1_AND_3);
    publishPage(PAGE_WITH_IMAGES_1_AND_3); // note: publish twice, to verify no extra duplicates are created in JCR
    // then
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_IMAGES_1_AND_3, IMAGE_1, IMAGE_3);
    // and
    verifyPublishedResourcesDataIsStored(PAGE_WITH_IMAGES_1_AND_3, IMAGE_1, IMAGE_3);
    // and
    verifyHashIsStored(IMAGE_1, "a5f27fe5339d01c3729c1a152f68c1d2f6fe4d6eebb421564e29158c4dfca9a8");
    verifyHashIsNotStored(IMAGE_2);
    verifyHashIsStored(IMAGE_3, "db8b5de55aec3168bbad32271a6cf2421c1e613cdf03713da47c6373b0d952e0");

    // when
    publishPage(PAGE_WITH_IMAGES_1_2_3);
    // then
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_IMAGES_1_AND_3, PAGE_WITH_IMAGES_1_2_3, IMAGE_1, IMAGE_2, IMAGE_3);
    // and
    verifyPublishedResourcesDataIsStored(PAGE_WITH_IMAGES_1_2_3, IMAGE_1, IMAGE_2, IMAGE_3);
    // and
    verifyHashIsStored(IMAGE_1, "a5f27fe5339d01c3729c1a152f68c1d2f6fe4d6eebb421564e29158c4dfca9a8");
    verifyHashIsStored(IMAGE_2, "54dfcf99c689cc1becdf12cefcf3aa9ea8fb5e91c74f510ec6454516e53afd2e");
    verifyHashIsStored(IMAGE_3, "db8b5de55aec3168bbad32271a6cf2421c1e613cdf03713da47c6373b0d952e0");

    // when
    unpublishPage(PAGE_WITH_IMAGES_1_2_3);
    // then
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_IMAGES_1_AND_3, IMAGE_1, IMAGE_3);
    // and
    verifyPublishedResourcesDataIsNotStored(PAGE_WITH_IMAGES_1_2_3);
    // and
    verifyHashIsStored(IMAGE_1, "a5f27fe5339d01c3729c1a152f68c1d2f6fe4d6eebb421564e29158c4dfca9a8");
    verifyHashIsNotStored(IMAGE_2);
    verifyHashIsStored(IMAGE_3, "db8b5de55aec3168bbad32271a6cf2421c1e613cdf03713da47c6373b0d952e0");

    // when
    unpublishPage(PAGE_WITH_IMAGES_1_AND_3);
    // then
    assertResourcesCurrentlyOnStreamX();
    // and
    verifyPublishedResourcesDataIsNotStored(PAGE_WITH_IMAGES_1_AND_3);
    // and
    verifyHashIsNotStored(IMAGE_1);
    verifyHashIsNotStored(IMAGE_2);
    verifyHashIsNotStored(IMAGE_3);
  }

  @Test
  void unpublishingRelatedResources_shouldWorkAlsoForRelatedResourcesOfOtherTypeThanAssets() {
    // when
    publishPage(PAGE_WITH_REFERENCED_PAGES_1_AND_3);
    // then
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_REFERENCED_PAGES_1_AND_3, REFERENCED_PAGE_1, REFERENCED_PAGE_3);

    // when
    publishPage(PAGE_WITH_REFERENCED_PAGES_1_2_3);
    // then
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_REFERENCED_PAGES_1_AND_3, PAGE_WITH_REFERENCED_PAGES_1_2_3, REFERENCED_PAGE_1, REFERENCED_PAGE_2, REFERENCED_PAGE_3);

    // when
    unpublishPage(PAGE_WITH_REFERENCED_PAGES_1_2_3);
    // then
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_REFERENCED_PAGES_1_AND_3, REFERENCED_PAGE_1, REFERENCED_PAGE_3);

    // when
    unpublishPage(PAGE_WITH_REFERENCED_PAGES_1_AND_3);
    // then
    assertResourcesCurrentlyOnStreamX();
  }

  @Test
  void shouldHandleBigNumberOfPagesWithBigNumberOfRelatedResources() {
    // given
    unregisterAllPages();
    final int N = 100; // pages and images max count
    final String imagePathFormat = "/content/dam/images/image-%d.jpg";

    Map<Integer, String> pagePaths = IntStream
        .rangeClosed(1, N).boxed()
        .collect(Collectors.toMap(
            Function.identity(),
            pageNumber -> "/content/my-site/page-" + pageNumber + ".html"
        ));

    // when: publish all the pages, each with references to images that have numbers from 1 to the page number (inclusive)
    pagePaths.forEach((pageNumber, pagePath) ->
        registerPage(pagePath,
            generateHtmlPageContent(
                IntStream.rangeClosed(1, pageNumber).boxed()
                    .map(imageNumber -> String.format(imagePathFormat, imageNumber))
                    .toArray(String[]::new)
            )
        )
    );
    publishPages(pagePaths.values());

    // and: unpublish pages 2,4,6,8,...,N
    unpublishPages(pagePaths.entrySet().stream()
        .filter(entry -> entry.getKey() % 2 == 0)
        .map(Entry::getValue)
        .collect(Collectors.toList())
    );

    // and: republish pages 1,3,5,7,...,N-1 edited to remove images 11,13,15,17,...,N-1
    pagePaths.forEach((pageNumber, pagePath) -> {
      if (pageNumber % 2 == 1) {
        editPage(pagePath, generateHtmlPageContent(
            IntStream.rangeClosed(1, pageNumber)
                .boxed()
                .filter(imageNumber -> imageNumber <= 10 || imageNumber % 2 == 0) // leave only images 1-10 and 12,14,16,...,N
                .map(imageNumber -> String.format(imagePathFormat, imageNumber))
                .toArray(String[]::new)
        ));
      }
    });
    publishPages(pagePaths.entrySet().stream()
        .filter(entry -> entry.getKey() % 2 == 1)
        .map(Entry::getValue)
        .collect(Collectors.toList())
    );

    // then:
    Set<String> expectedResourcesOnStreamX = new TreeSet<>();
    // expect pages 1,3,5,7,...,N-1 on StreamX
    pagePaths.entrySet().stream()
        .filter(entry -> entry.getKey() % 2 == 1)
        .map(Entry::getValue)
        .forEach(expectedResourcesOnStreamX::add);
    // expect images 1-10 and 12,14,16,...,N-1 on StreamX
    IntStream.rangeClosed(1, N)
        .boxed()
        .filter(imageNumber -> imageNumber <= 10 || imageNumber % 2 == 0)
        .filter(imageNumber -> !imageNumber.equals(N)) // last page was unpublished and was the only page referencing image N
        .map(imageNumber -> String.format(imagePathFormat, imageNumber))
        .forEach(expectedResourcesOnStreamX::add);

    assertResourcesCurrentlyOnStreamX(expectedResourcesOnStreamX);

    // and: expect only the edited republished pages 1,3,5,...,N-1 to be published two times
    pagePaths.forEach((pageNumber, pagePath) -> {
      if (pageNumber % 2 == 0) {
        assertPublishedTimes(pagePath, 1);
      } else {
        assertPublishedTimes(pagePath, 2);
      }
    });

    // and: expect images 1...N to be published exactly once, since their content never changes
    for (int i = 1; i < N; i++) {
      String imagePath = String.format(imagePathFormat, i);
      assertPublishedTimes(imagePath, 1);
    }

    // final assertion: make sure publication (along with the JCR operations) are efficient enough
    assertThat(publicationServiceProcessingTotalTimeMillis).isLessThan(1000);
  }

  private String registerPage(String pageResourcePath, String content) {
    allTestPages.put(pageResourcePath, content);
    loadPageToSlingContext(pageResourcePath);
    return pageResourcePath;
  }

  private void editPage(String pageResourcePath, String content) {
    allTestPages.put(pageResourcePath, content);
  }

  private void unregisterAllPages() {
    for (String pageResourcePath : allTestPages.keySet()) {
      unloadPageFromSlingContext(pageResourcePath);
    }
    allTestPages.clear();
  }

  private void loadPageToSlingContext(String pageResourcePath) {
    slingContext.load().json(PAGE_JSON_RESOURCE_FILE_PATH, pageResourcePath);
  }

  private void unloadPageFromSlingContext(String pageResourcePath) {
    Resource resource = Objects.requireNonNull(resourceResolver.getResource(pageResourcePath));
    try {
      resourceResolver.delete(resource);
    } catch (PersistenceException e) {
      fail(e.getMessage());
    }
  }

  private static String generateHtmlPageContent(String... relatedResourcePathsToInclude) {
    return Arrays.stream(relatedResourcePathsToInclude)
        .map(path -> "<include path='" + path + "' />") // any include tag, doesn't have to exist in HTML language
        .collect(Collectors.joining("\n", "<html><body>", "</body></html>"));
  }

  private void verifyPublishedResourcesDataIsStored(String parentPagePath, String... expectedRelatedAssetPaths) {
    String expectedJcrPath = "/var/streamx/connector/sling/resources/published/grouped-by-parent-resource-path" + parentPagePath;
    Resource jcrResource = resourceResolver.getResource(expectedJcrPath);
    assertThat(jcrResource).isNotNull();
    assertThat(jcrResource.getValueMap()).containsEntry("relatedResources",
        Arrays.stream(expectedRelatedAssetPaths)
            .map(relatedAssetPath -> relatedAssetPath + "`@`" + "dam:Asset")
            .toArray(String[]::new));
  }

  private void verifyPublishedResourcesDataIsNotStored(String parentPagePath) {
    Resource relatedResourcesForParent = resourceResolver.getResource("/var/streamx/connector/sling/resources/published/grouped-by-parent-resource-path" + parentPagePath);
    assertThat(relatedResourcesForParent).isNull();
  }

  private void verifyHashIsStored(String resourcePath, String expectedHash) {
    Resource hashResource = resourceResolver.getResource("/var/streamx/connector/sling/resources/hashes" + resourcePath);
    assertThat(hashResource).isNotNull();
    assertThat(hashResource.getValueMap()).containsEntry("lastPublishHash", expectedHash);
  }

  private void verifyHashIsNotStored(String resourcePath) {
    Resource hashResource = resourceResolver.getResource("/var/streamx/connector/sling/resources/hashes" + resourcePath);
    assertThat(hashResource).isNull();
  }

  private void publishPage(String resourcePath) {
    publishPages(List.of(resourcePath));
  }

  private void publishPages(Collection<String> resourcePaths) {
    ingestPages(resourcePaths, PublicationAction.PUBLISH);
  }

  private void unpublishPage(String resourcePath) {
    unpublishPages(List.of(resourcePath));
  }

  private void unpublishPages(Collection<String> resourcePaths) {
    ingestPages(resourcePaths, PublicationAction.UNPUBLISH);
  }

  private void ingestPages(Collection<String> resourcePaths, PublicationAction action) {
    doReturn(action.name())
        .when(job)
        .getProperty(PN_STREAMX_INGESTION_ACTION, String.class);

    doReturn(resourcePaths.stream().map(path -> new PageResourceInfo(path).serialize()).toArray(String[]::new))
        .when(job)
        .getProperty(PN_STREAMX_RESOURCES_INFO, String[].class);

    long startTimeNanos = System.nanoTime();
    publicationService.process(job, jobExecutionContext);
    long elapsedTimeNanos = System.nanoTime() - startTimeNanos;
    publicationServiceProcessingTotalTimeMillis += Duration.ofNanos(elapsedTimeNanos).toMillis();
  }

  private void assertPublishedTimes(String resourcePath, int times) {
    assertIngestedTimes(resourcePath, times, PublicationAction.PUBLISH);
  }

  private void assertUnpublishedTimes(String resourcePath, int times) {
    assertIngestedTimes(resourcePath, times, PublicationAction.UNPUBLISH);
  }

  private void assertIngestedTimes(String resourcePath, int times, PublicationAction action) {
    long actualIngestedTimes = jobManager.getJobQueue().stream()
        .filter(job -> job.getTopic().equals("dev/streamx/publications"))
        .filter(job -> job.hasProperty(PN_STREAMX_ACTION, action.name()))
        .filter(job -> job.hasProperty(PN_STREAMX_PATH, resourcePath))
        .count();
    assertThat(actualIngestedTimes).describedAs(resourcePath).isEqualTo(times);
  }

  private void assertResourcesCurrentlyOnStreamX(String... expectedResourcePaths) {
    assertResourcesCurrentlyOnStreamX(new TreeSet<>(Set.of(expectedResourcePaths)));
  }

  private void assertResourcesCurrentlyOnStreamX(Set<String> expectedResourcePaths) {
    Set<String> actualResourcePaths = new TreeSet<>();
    for (var job : jobManager.getJobQueue()) {
      String action = job.getProperty(PN_STREAMX_ACTION, String.class);
      String resourcePath = job.getProperty(PN_STREAMX_PATH, String.class);
      if (action.equals(PublicationAction.PUBLISH.name())) {
        actualResourcePaths.add(resourcePath);
      } else if (action.equals(PublicationAction.UNPUBLISH.name())) {
        actualResourcePaths.remove(resourcePath);
      }
    }

    assertThat(actualResourcePaths).containsExactlyInAnyOrderElementsOf(expectedResourcePaths);
  }
}
