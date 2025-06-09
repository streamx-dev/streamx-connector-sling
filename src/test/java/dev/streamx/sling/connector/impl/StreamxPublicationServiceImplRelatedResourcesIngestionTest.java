package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.IngestionTriggerJobHelper.PN_STREAMX_INGESTION_ACTION;
import static dev.streamx.sling.connector.impl.IngestionTriggerJobHelper.PN_STREAMX_RESOURCES_INFO;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_ACTION;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.selectors.content.ResourceContentRelatedResourcesSelector;
import dev.streamx.sling.connector.selectors.content.ResourceContentRelatedResourcesSelectorConfig;
import dev.streamx.sling.connector.test.util.ResourceMocks;
import dev.streamx.sling.connector.testing.handlers.AssetPublicationHandler;
import dev.streamx.sling.connector.testing.handlers.PagePublicationHandler;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJobManager;
import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

  private static final String DAM_ASSET = "dam:Asset";
  private static final String CQ_PAGE = "cq:Page";

  private static final String IMAGE_1 = "/content/firsthops/us/en/image-1.jpg";
  private static final String IMAGE_2 = "/content/firsthops/us/en/image-2.jpg";
  private static final String IMAGE_3 = "/content/firsthops/us/en/image-3.jpg";

  private static final PageInfo PAGE_WITH_IMAGES_1_2_3 = new PageInfo(
      "/content/my-site/us/en/page-with-images-1-2-3.html",
      "src/test/resources/page-with-images-1-2-3.html"
  );

  private static final PageInfo PAGE_WITH_IMAGES_1_AND_3 = new PageInfo(
      "/content/my-site/us/en/page-with-images-1-and-3.html",
      "src/test/resources/page-with-images-1-and-3.html"
  );

  private static final String REFERENCED_PAGE_1 = "/content/my-site/us/en/referenced-page-1.html";
  private static final String REFERENCED_PAGE_2 = "/content/my-site/us/en/referenced-page-2.html";
  private static final String REFERENCED_PAGE_3 = "/content/my-site/us/en/referenced-page-3.html";

  private static final PageInfo PAGE_WITH_REFERENCED_PAGES_1_2_3 = new PageInfo(
      "/content/my-site/us/en/page-with-referenced-pages-1-2-3.html",
      "src/test/resources/page-with-referenced-pages-1-2-3.html"
  );

  private static final PageInfo PAGE_WITH_REFERENCED_PAGES_1_AND_3 = new PageInfo(
      "/content/my-site/us/en/page-with-referenced-pages-1-and-3.html",
      "src/test/resources/page-with-referenced-pages-1-and-3.html"
  );

  private static final List<PageInfo> allTestPages = List.of(
      PAGE_WITH_IMAGES_1_2_3,
      PAGE_WITH_IMAGES_1_AND_3,
      PAGE_WITH_REFERENCED_PAGES_1_2_3,
      PAGE_WITH_REFERENCED_PAGES_1_AND_3
  );

  private final SlingContext slingContext = new SlingContext(ResourceResolverType.JCR_OAK);
  private final ResourceResolver resourceResolver = spy(slingContext.resourceResolver());
  private final ResourceResolverFactory resourceResolverFactory = mock(ResourceResolverFactory.class);
  private final FakeJobManager jobManager = new FakeJobManager(Collections.emptyList());
  private final StreamxPublicationServiceImpl publicationService = new StreamxPublicationServiceImpl();
  private final Job job = mock(Job.class);
  private final JobExecutionContext jobExecutionContext = mock(JobExecutionContext.class);

  private final AtomicBoolean shouldRequestProcessorSimulateAllResourcesAreEdited = new AtomicBoolean(false);

  private final SlingRequestProcessor requestProcessor = (HttpServletRequest request, HttpServletResponse response, ResourceResolver resourceResolver) -> {
    String requestURI = request.getRequestURI();
    response.setContentType("text/html");
    for (PageInfo pageInfo : allTestPages) {
      if (requestURI.equals(pageInfo.resourcePath)) {
        response.getWriter().write(pageInfo.content);
        break;
      }
    }
    if (requestURI.endsWith(".jpg")) {
      response.getWriter().write("Content of image " + requestURI);
    }

    if (shouldRequestProcessorSimulateAllResourcesAreEdited.get()) {
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

  @BeforeEach
  void setup() throws Exception {
    configureResources();
    configureStreamxClient();
    configureServices();
  }

  private void configureResources() throws Exception {
    for (PageInfo pageInfo : allTestPages) {
      slingContext.load().json(pageInfo.jsonResourceFile, pageInfo.resourcePath);
    }

    doReturn(ResourceMocks.createResourceMock(DAM_ASSET))
        .when(resourceResolver)
        .resolve(ArgumentMatchers.<String>argThat(path -> path.endsWith(".jpg")));

    doReturn(ResourceMocks.createResourceMock(CQ_PAGE))
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
    shouldRequestProcessorSimulateAllResourcesAreEdited.set(false);
    shouldUnpublishRelatedResources_WhenUnpublishingParentPage_IfNoMoreReferences();
  }

  @Test
  void shouldUnpublishRelatedResources_WhenUnpublishingParentPage_IfNoMoreReferences_WhenResourcesAreEditedBetweenEachRequest() {
    shouldRequestProcessorSimulateAllResourcesAreEdited.set(true);
    shouldUnpublishRelatedResources_WhenUnpublishingParentPage_IfNoMoreReferences();
  }

  private void shouldUnpublishRelatedResources_WhenUnpublishingParentPage_IfNoMoreReferences() {
    // given
    boolean areResourcesEditedBeforeEachRequest = shouldRequestProcessorSimulateAllResourcesAreEdited.get();

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
    assertPublishedTimes(IMAGE_1, areResourcesEditedBeforeEachRequest ? 2 : 1);
    assertPublishedTimes(IMAGE_2, 1);
    assertPublishedTimes(IMAGE_3, areResourcesEditedBeforeEachRequest ? 2 : 1);
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_IMAGES_1_2_3, PAGE_WITH_IMAGES_1_AND_3, IMAGE_1, IMAGE_2, IMAGE_3);

    // when 3: unpublish first page
    unpublishPage(PAGE_WITH_IMAGES_1_2_3);

    // then: expect image 2 to be gone, as it doesn't have any references anymore
    assertPublishedTimes(IMAGE_1, areResourcesEditedBeforeEachRequest ? 2 : 1);
    assertPublishedTimes(IMAGE_2, 1);
    assertPublishedTimes(IMAGE_3, areResourcesEditedBeforeEachRequest ? 2 : 1);
    assertUnpublishedTimes(IMAGE_1, 0);
    assertUnpublishedTimes(IMAGE_2, 1);
    assertUnpublishedTimes(IMAGE_3, 0);
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_IMAGES_1_AND_3, IMAGE_1, IMAGE_3);

    // when 4: in next step we will unpublish the second page, but first publish one of its images directly
    publishImage(IMAGE_3);

    // then
    assertPublishedTimes(IMAGE_1, areResourcesEditedBeforeEachRequest ? 2 : 1);
    assertPublishedTimes(IMAGE_2, 1);
    assertPublishedTimes(IMAGE_3, areResourcesEditedBeforeEachRequest ? 3 : 2); // note: by design, the feature of not re-publishing unchanged images is implemented only for related resources, it's not executed when an image is published directly
    assertUnpublishedTimes(IMAGE_1, 0);
    assertUnpublishedTimes(IMAGE_2, 1);
    assertUnpublishedTimes(IMAGE_3, 0);
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_IMAGES_1_AND_3, IMAGE_1, IMAGE_3);

    // when 5: unpublish second page
    unpublishPage(PAGE_WITH_IMAGES_1_AND_3);

    // then: expect image 1 to be gone, as it doesn't have any references anymore, but image 3 should stay in StreamX, since it was published directly
    assertPublishedTimes(IMAGE_1, areResourcesEditedBeforeEachRequest ? 2 : 1);
    assertPublishedTimes(IMAGE_2, 1);
    assertPublishedTimes(IMAGE_3, areResourcesEditedBeforeEachRequest ? 3 : 2);
    assertUnpublishedTimes(IMAGE_1, 1);
    assertUnpublishedTimes(IMAGE_2, 1);
    assertUnpublishedTimes(IMAGE_3, 0);
    assertResourcesCurrentlyOnStreamX(IMAGE_3);

    // when 6: publish first page again
    publishPage(PAGE_WITH_IMAGES_1_2_3);

    // then
    assertPublishedTimes(IMAGE_1, areResourcesEditedBeforeEachRequest ? 3 : 2);
    assertPublishedTimes(IMAGE_2, 2);
    assertPublishedTimes(IMAGE_3, areResourcesEditedBeforeEachRequest ? 4 : 2);
    assertUnpublishedTimes(IMAGE_1, 1);
    assertUnpublishedTimes(IMAGE_2, 1);
    assertUnpublishedTimes(IMAGE_3, 0);
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_IMAGES_1_2_3, IMAGE_1, IMAGE_2, IMAGE_3);

    // when 7: unpublish image directly
    unpublishImage(IMAGE_3);

    // then: expect to allow to always unpublish a main resource
    assertPublishedTimes(IMAGE_1, areResourcesEditedBeforeEachRequest ? 3 : 2);
    assertPublishedTimes(IMAGE_2, 2);
    assertPublishedTimes(IMAGE_3, areResourcesEditedBeforeEachRequest ? 4 : 2);
    assertUnpublishedTimes(IMAGE_1, 1);
    assertUnpublishedTimes(IMAGE_2, 1);
    assertUnpublishedTimes(IMAGE_3, 1);
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_IMAGES_1_2_3, IMAGE_1, IMAGE_2);

    // when 8: unpublish first page again
    unpublishPage(PAGE_WITH_IMAGES_1_2_3);

    // then: since no image is currently published directly - expecting all images (related resources) from the page to be unpublished
    assertPublishedTimes(IMAGE_1, areResourcesEditedBeforeEachRequest ? 3 : 2);
    assertPublishedTimes(IMAGE_2, 2);
    assertPublishedTimes(IMAGE_3, areResourcesEditedBeforeEachRequest ? 4 : 2);
    assertUnpublishedTimes(IMAGE_1, 2);
    assertUnpublishedTimes(IMAGE_2, 2);
    assertUnpublishedTimes(IMAGE_3, 2);
    assertResourcesCurrentlyOnStreamX();
  }

  @Test
  void shouldNotUnpublishImage_WhileUnpublishingPageThatReferencesTheImage_IfTheImageWasPublishedDirectlyBefore() {
    // when
    publishImage(IMAGE_1);
    // then
    assertResourcesCurrentlyOnStreamX(IMAGE_1);
    // and: also verify additional JCR store of published resources
    verifyPublishedResourcesStored(IMAGE_1);
    // and
    verifyHashNotStored(IMAGE_1);

    // when
    publishPage(PAGE_WITH_IMAGES_1_AND_3);
    publishPage(PAGE_WITH_IMAGES_1_AND_3); // note: publish twice, to verify no extra duplicates are created in JCR
    // then
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_IMAGES_1_AND_3, IMAGE_1, IMAGE_3);
    // and
    verifyPublishedResourcesStored(
        PAGE_WITH_IMAGES_1_AND_3.resourcePath,
        new ResourceInfo(IMAGE_1, DAM_ASSET),
        new ResourceInfo(IMAGE_3, DAM_ASSET)
    );
    // and
    verifyHashStored(IMAGE_1, "a5f27fe5339d01c3729c1a152f68c1d2f6fe4d6eebb421564e29158c4dfca9a8");
    verifyHashNotStored(IMAGE_2);
    verifyHashStored(IMAGE_3, "db8b5de55aec3168bbad32271a6cf2421c1e613cdf03713da47c6373b0d952e0");

    // when
    unpublishPage(PAGE_WITH_IMAGES_1_AND_3);
    // then
    assertResourcesCurrentlyOnStreamX(IMAGE_1);
    // and
    verifyPublishedResourcesStored(IMAGE_1);
    verifyPublishedResourcesNotStored(PAGE_WITH_IMAGES_1_AND_3.resourcePath);
    // and
    verifyHashStored(IMAGE_1, "a5f27fe5339d01c3729c1a152f68c1d2f6fe4d6eebb421564e29158c4dfca9a8");
    verifyHashNotStored(IMAGE_2);
    verifyHashNotStored(IMAGE_3);
  }

  private void verifyPublishedResourcesStored(String parentResourcePath, ResourceInfo... relatedResources) {
    Resource relatedResourcesForParent = resourceResolver.getResource("/var/streamx/connector/sling/resources/published" + parentResourcePath);
    assertThat(relatedResourcesForParent).isNotNull();
    assertThat(relatedResourcesForParent.getChildren())
        .hasSameSizeAs(relatedResources)
        .extracting(child -> new ResourceInfo(
            child.getValueMap().get("path", String.class),
            child.getValueMap().get("primaryNodeType", String.class)))
        .containsExactly(relatedResources);
  }

  private void verifyPublishedResourcesNotStored(String parentResourcePath) {
    Resource relatedResourcesForParent = resourceResolver.getResource("/var/streamx/connector/sling/resources/published" + parentResourcePath);
    assertThat(relatedResourcesForParent).isNull();
  }

  private void verifyHashStored(String resourcePath, String expected) {
    Resource hashResource = resourceResolver.getResource("/var/streamx/connector/sling/resources/hashes" + resourcePath);
    String hash = hashResource.getValueMap().get("lastPublishHash", String.class);
    assertThat(hash).isEqualTo(expected);
  }

  private void verifyHashNotStored(String resourcePath) {
    Resource hashResource = resourceResolver.getResource("/var/streamx/connector/sling/resources/hashes" + resourcePath);
    assertThat(hashResource).isNull();
  }

  @Test
  void unpublishingRelatedResources_shouldWorkAlsoForRelatedResourcesOfOtherTypeThanAssets() {
    // when
    publishPage(REFERENCED_PAGE_1);
    // then
    assertResourcesCurrentlyOnStreamX(REFERENCED_PAGE_1);

    // when
    publishPage(PAGE_WITH_REFERENCED_PAGES_1_AND_3);
    // then
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_REFERENCED_PAGES_1_AND_3, REFERENCED_PAGE_1, REFERENCED_PAGE_3);

    // when
    unpublishPage(PAGE_WITH_REFERENCED_PAGES_1_AND_3);
    // then
    assertResourcesCurrentlyOnStreamX(REFERENCED_PAGE_1);

    // when
    publishPage(PAGE_WITH_REFERENCED_PAGES_1_2_3);
    // then
    assertResourcesCurrentlyOnStreamX(PAGE_WITH_REFERENCED_PAGES_1_2_3, REFERENCED_PAGE_1, REFERENCED_PAGE_2, REFERENCED_PAGE_3);

    // when
    unpublishPage(REFERENCED_PAGE_1);
    unpublishPage(PAGE_WITH_REFERENCED_PAGES_1_2_3);
    // then
    assertResourcesCurrentlyOnStreamX();
  }

  private void publishPage(PageInfo page) {
    publishPage(page.resourcePath);
  }

  private void unpublishPage(PageInfo page) {
    unpublishPage(page.resourcePath);
  }

  private void publishPage(String resourcePath) {
    ingest(new ResourceInfo(resourcePath, CQ_PAGE), PublicationAction.PUBLISH);
  }

  private void unpublishPage(String resourcePath) {
    ingest(new ResourceInfo(resourcePath, CQ_PAGE), PublicationAction.UNPUBLISH);
  }

  private void publishImage(String resourcePath) {
    ingest(new ResourceInfo(resourcePath, DAM_ASSET), PublicationAction.PUBLISH);
  }

  private void unpublishImage(String resourcePath) {
    ingest(new ResourceInfo(resourcePath, DAM_ASSET), PublicationAction.UNPUBLISH);
  }

  private void ingest(ResourceInfo resourceInfo, PublicationAction action) {
    doReturn(action.name())
        .when(job)
        .getProperty(PN_STREAMX_INGESTION_ACTION, String.class);

    doReturn(new String[] {resourceInfo.serialize()})
        .when(job)
        .getProperty(PN_STREAMX_RESOURCES_INFO, String[].class);

    publicationService.process(job, jobExecutionContext);
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
    assertThat(actualIngestedTimes).isEqualTo(times);
  }

  private void assertResourcesCurrentlyOnStreamX(Object... expectedResources) {
    Set<String> actualResourcePaths = new LinkedHashSet<>();
    for (var job : jobManager.getJobQueue()) {
      String action = job.getProperty(PN_STREAMX_ACTION, String.class);
      String resourcePath = job.getProperty(PN_STREAMX_PATH, String.class);
      if (action.equals(PublicationAction.PUBLISH.name())) {
        actualResourcePaths.add(resourcePath);
      } else if (action.equals(PublicationAction.UNPUBLISH.name())) {
        actualResourcePaths.remove(resourcePath);
      }
    }

    List<String> expectedResourcePaths = Arrays.stream(expectedResources)
        .map(item -> item instanceof PageInfo ? ((PageInfo) item).resourcePath : (String) item)
        .collect(Collectors.toList());

    assertThat(actualResourcePaths).containsExactlyInAnyOrderElementsOf(expectedResourcePaths);
  }

  private static class PageInfo {
    private final String resourcePath;
    private final String jsonResourceFile;
    private final String content;

    private PageInfo(String resourcePath, String contentResourceFile) {
      this.resourcePath = resourcePath;
      this.jsonResourceFile = "src/test/resources/page.json";
      this.content = contentOf(new File(contentResourceFile));
    }
  }
}
