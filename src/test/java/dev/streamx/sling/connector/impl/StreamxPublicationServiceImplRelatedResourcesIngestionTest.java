package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_ACTION;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PUBLICATION_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.handlers.resourcepath.AssetResourcePathPublicationHandler;
import dev.streamx.sling.connector.handlers.resourcepath.AssetResourcePathPublicationHandlerConfig;
import dev.streamx.sling.connector.selectors.content.ResourceContentRelatedResourcesSelector;
import dev.streamx.sling.connector.selectors.content.ResourceContentRelatedResourcesSelectorConfig;
import dev.streamx.sling.connector.test.util.JcrTreeReader;
import dev.streamx.sling.connector.test.util.PageResourceInfo;
import dev.streamx.sling.connector.test.util.ResourceContentRelatedResourcesSelectorConfigImpl;
import dev.streamx.sling.connector.test.util.ResourceResolverMocks;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionContext.ResultBuilder;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlingContextExtension.class)
class StreamxPublicationServiceImplRelatedResourcesIngestionTest {

  private static final String PAGE_1 = "/content/my-site/en/us/page-1";
  private static final String PAGE_2 = "/content/my-site/en/us/page-2";

  private static final String CORE_IMG_FOR_PAGE_1 = "/content/my-site/en/us/page-1/images/image.coreimg.jpg/11111/foo.jpg";
  private static final String CORE_IMG_FOR_PAGE_2 = "/content/my-site/en/us/page-2/images/image.coreimg.jpg/22222/foo.jpg";

  private static final String GLOBAL_IMAGE = "/content/dam/bar.jpg";
  private static final String GLOBAL_CSS_CLIENTLIB = "/etc.clientlibs/clientlib-1.js";
  private static final String GLOBAL_JS_CLIENTLIB = "/etc.clientlibs/clientlib-1.css";

  private static final String PAGE_JSON_RESOURCE_FILE_PATH = "src/test/resources/page.json";

  private final SlingContext slingContext = new SlingContext(ResourceResolverType.JCR_OAK);
  private final ResourceResolver resourceResolver = spy(slingContext.resourceResolver());
  private final ResourceResolverFactory resourceResolverFactory = mock(ResourceResolverFactory.class);
  private final FakeJobManager jobManager = spy(new FakeJobManager(Collections.emptyList()));
  private final ResultBuilder resultBuilderMock = mock(ResultBuilder.class);
  private final StreamxPublicationServiceImpl publicationService = new StreamxPublicationServiceImpl();
  private final JobExecutionContext jobExecutionContext = mock(JobExecutionContext.class);


  // resource path + content
  private final Map<String, String> allTestResources = new LinkedHashMap<>();

  private final SlingRequestProcessor requestProcessor = (HttpServletRequest request, HttpServletResponse response, ResourceResolver resourceResolver) -> {
    String requestURI = request.getRequestURI();
    assertThat(allTestResources.keySet()).contains(requestURI);
    response.setContentType("text/html");
    response.getWriter().write(allTestResources.get(requestURI));
  };

  private final ResourceContentRelatedResourcesSelectorConfig relatedResourcesConfig = new ResourceContentRelatedResourcesSelectorConfigImpl()
      .withReferencesSearchRegexes("(/content/.+coreimg.+\\.jpg)", "(/etc.clientlibs/.+\\.(css|js))")
      .withResourcePathPostfixToAppend("")
      .withResourceRequiredPathRegex(".*")
      .withResourceRequiredPrimaryNodeTypeRegex(".*")
      .withRelatedResourceProcessablePathRegex(".*\\.html$");

  private final AssetResourcePathPublicationHandlerConfig assetResourcePathPublicationHandlerConfig = new AssetResourcePathPublicationHandlerConfig() {

    @Override
    public Class<? extends Annotation> annotationType() {
      return AssetResourcePathPublicationHandlerConfig.class;
    }

    @Override
    public boolean enabled() {
      return true;
    }

    @Override
    public String assets_path_regexp() {
      return ".*\\.(jpg|css|js)";
    }

    @Override
    public String publication_channel() {
      return "assets";
    }
  };

  private ResourceContentRelatedResourcesSelector selector;
  private IngestionTriggerJobExecutor ingestionTriggerJobExecutor;
  private long publicationServiceProcessingTotalTimeMillis = 0;

  @BeforeEach
  void setup() throws Exception {
    ResourceResolverMocks.configure(resourceResolver, resourceResolverFactory);
    configureStreamxClient();
    configureServices();
  }

  private void configureServices() {
    slingContext.registerService(SlingRequestProcessor.class, requestProcessor);
    selector = new ResourceContentRelatedResourcesSelector(relatedResourcesConfig, requestProcessor, resourceResolverFactory);
    slingContext.registerService(RelatedResourcesSelector.class, selector);
    slingContext.registerInjectActivateService(new RelatedResourcesSelectorRegistry());

    slingContext.registerService(PublicationHandler.class, new PagePublicationHandler(resourceResolver));
    slingContext.registerService(PublicationHandler.class, new AssetResourcePathPublicationHandler(resourceResolverFactory, requestProcessor, assetResourcePathPublicationHandlerConfig));
    slingContext.registerInjectActivateService(new PublicationHandlerRegistry());

    doReturn(resultBuilderMock).when(resultBuilderMock).message(anyString());
    doReturn(mock(JobExecutionResult.class)).when(resultBuilderMock).failed();
    doReturn(resultBuilderMock).when(jobExecutionContext).result();
    slingContext.registerService(JobManager.class, jobManager);

    slingContext.registerInjectActivateService(publicationService);
    ingestionTriggerJobExecutor = slingContext.registerInjectActivateService(IngestionTriggerJobExecutor.class);
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
  void shouldPublishPagesAndAllRelatedResourcesThatAreConfigureToBeFound_AndUnpublishOwnCoreImagesAlongWithPage() throws Exception {
    // given
    String page1WithImagesAndCss = registerResource(
        PAGE_1,
        generateHtmlPageContent(CORE_IMG_FOR_PAGE_1, GLOBAL_IMAGE, GLOBAL_CSS_CLIENTLIB)
    );
    String page2WithImagesAndJs = registerResource(
        PAGE_2,
        generateHtmlPageContent(CORE_IMG_FOR_PAGE_2, GLOBAL_IMAGE, GLOBAL_JS_CLIENTLIB)
    );

    // when 1:
    publishPage(page1WithImagesAndCss);
    publishPage(page2WithImagesAndJs);
    publishPage(page2WithImagesAndJs);
    publishPage(page1WithImagesAndCss); // publish twice - to test skipping republishing of already published related resources

    // then
    assertPublishedTimes(Map.of(
        CORE_IMG_FOR_PAGE_1, 1,
        CORE_IMG_FOR_PAGE_2, 1,
        GLOBAL_IMAGE, 0,
        GLOBAL_CSS_CLIENTLIB, 1,
        GLOBAL_JS_CLIENTLIB, 1
    ));

    assertNoUnpublishes();

    assertResourcesCurrentlyOnStreamX(page1WithImagesAndCss, page2WithImagesAndJs, CORE_IMG_FOR_PAGE_1, CORE_IMG_FOR_PAGE_2, GLOBAL_CSS_CLIENTLIB, GLOBAL_JS_CLIENTLIB);

    verifyStateOfPublishedResourcesData(
        Map.of(
            PAGE_1, Set.of(CORE_IMG_FOR_PAGE_1, GLOBAL_CSS_CLIENTLIB),
            PAGE_2, Set.of(CORE_IMG_FOR_PAGE_2, GLOBAL_JS_CLIENTLIB)
        ),
        Set.of(
            CORE_IMG_FOR_PAGE_1,
            CORE_IMG_FOR_PAGE_2,
            GLOBAL_CSS_CLIENTLIB,
            GLOBAL_JS_CLIENTLIB
        )
    );

    // ---------
    // when 2:
    unpublishPage(page1WithImagesAndCss);

    // then: expect only the own image to be unpublished along with the page
    assertPublishedTimes(Map.of(
        CORE_IMG_FOR_PAGE_1, 1,
        CORE_IMG_FOR_PAGE_2, 1,
        GLOBAL_IMAGE, 0,
        GLOBAL_CSS_CLIENTLIB, 1,
        GLOBAL_JS_CLIENTLIB, 1
    ));

    assertUnpublishedTimes(Map.of(
        CORE_IMG_FOR_PAGE_1, 1,
        CORE_IMG_FOR_PAGE_2, 0,
        GLOBAL_IMAGE, 0,
        GLOBAL_CSS_CLIENTLIB, 0,
        GLOBAL_JS_CLIENTLIB, 0
    ));

    assertResourcesCurrentlyOnStreamX(page2WithImagesAndJs, CORE_IMG_FOR_PAGE_2, GLOBAL_CSS_CLIENTLIB, GLOBAL_JS_CLIENTLIB);

    verifyStateOfPublishedResourcesData(
        Map.of(
            PAGE_2, Set.of(CORE_IMG_FOR_PAGE_2, GLOBAL_JS_CLIENTLIB)
        ),
        Set.of(
            CORE_IMG_FOR_PAGE_2,
            GLOBAL_JS_CLIENTLIB,
            GLOBAL_CSS_CLIENTLIB
        )
    );

    // ---------
    // when 3:
    unpublishPage(page2WithImagesAndJs);

    // then: expect only the own image to be unpublished along with the page
    assertPublishedTimes(Map.of(
        CORE_IMG_FOR_PAGE_1, 1,
        CORE_IMG_FOR_PAGE_2, 1,
        GLOBAL_IMAGE, 0,
        GLOBAL_CSS_CLIENTLIB, 1,
        GLOBAL_JS_CLIENTLIB, 1
    ));

    assertUnpublishedTimes(Map.of(
        CORE_IMG_FOR_PAGE_1, 1,
        CORE_IMG_FOR_PAGE_2, 1,
        GLOBAL_IMAGE, 0,
        GLOBAL_CSS_CLIENTLIB, 0,
        GLOBAL_JS_CLIENTLIB, 0
    ));

    assertResourcesCurrentlyOnStreamX(GLOBAL_CSS_CLIENTLIB, GLOBAL_JS_CLIENTLIB);

    verifyStateOfPublishedResourcesData(
        Collections.emptyMap(),
        Set.of(
            GLOBAL_JS_CLIENTLIB,
            GLOBAL_CSS_CLIENTLIB
        )
    );

    // ---------
    // when 4: publish page 2 again
    publishPage(page2WithImagesAndJs);

    // then
    assertPublishedTimes(Map.of(
        CORE_IMG_FOR_PAGE_1, 1,
        CORE_IMG_FOR_PAGE_2, 2,
        GLOBAL_IMAGE, 0,
        GLOBAL_CSS_CLIENTLIB, 1,
        GLOBAL_JS_CLIENTLIB, 1
    ));

    assertUnpublishedTimes(Map.of(
        CORE_IMG_FOR_PAGE_1, 1,
        CORE_IMG_FOR_PAGE_2, 1,
        GLOBAL_IMAGE, 0,
        GLOBAL_CSS_CLIENTLIB, 0,
        GLOBAL_JS_CLIENTLIB, 0
    ));

    assertResourcesCurrentlyOnStreamX(page2WithImagesAndJs, CORE_IMG_FOR_PAGE_2, GLOBAL_CSS_CLIENTLIB, GLOBAL_JS_CLIENTLIB);

    verifyStateOfPublishedResourcesData(
        Map.of(
            PAGE_2, Set.of(CORE_IMG_FOR_PAGE_2, GLOBAL_JS_CLIENTLIB)
        ),
        Set.of(
            CORE_IMG_FOR_PAGE_2,
            GLOBAL_JS_CLIENTLIB,
            GLOBAL_CSS_CLIENTLIB
        )
    );
  }

  @Test
  void shouldUnpublishUnreferencedOwnImageWhenPublishingEditedPage() throws Exception {
    // given
    String page1WithImagesAndCss = registerResource(
        PAGE_1,
        generateHtmlPageContent(CORE_IMG_FOR_PAGE_1, GLOBAL_IMAGE, GLOBAL_CSS_CLIENTLIB)
    );
    String page2WithImagesAndJs = registerResource(
        PAGE_2,
        generateHtmlPageContent(CORE_IMG_FOR_PAGE_2, GLOBAL_IMAGE, GLOBAL_JS_CLIENTLIB)
    );

    // when 1:
    publishPage(page1WithImagesAndCss);
    publishPage(page2WithImagesAndJs);

    // then
    assertPublishedTimes(Map.of(
        CORE_IMG_FOR_PAGE_1, 1,
        CORE_IMG_FOR_PAGE_2, 1,
        GLOBAL_IMAGE, 0,
        GLOBAL_CSS_CLIENTLIB, 1,
        GLOBAL_JS_CLIENTLIB, 1
    ));

    assertNoUnpublishes();

    assertResourcesCurrentlyOnStreamX(page1WithImagesAndCss, page2WithImagesAndJs, CORE_IMG_FOR_PAGE_1, CORE_IMG_FOR_PAGE_2, GLOBAL_CSS_CLIENTLIB, GLOBAL_JS_CLIENTLIB);

    verifyStateOfPublishedResourcesData(
        Map.of(
            PAGE_1, Set.of(CORE_IMG_FOR_PAGE_1, GLOBAL_CSS_CLIENTLIB),
            PAGE_2, Set.of(CORE_IMG_FOR_PAGE_2, GLOBAL_JS_CLIENTLIB)
        ),
        Set.of(
            CORE_IMG_FOR_PAGE_1,
            CORE_IMG_FOR_PAGE_2,
            GLOBAL_JS_CLIENTLIB,
            GLOBAL_CSS_CLIENTLIB
        )
    );

    // ---------
    // when 2: edit the page to remove all related resources from its content
    editResourceToRemoveAllRelatedResources(page1WithImagesAndCss);
    publishPage(page1WithImagesAndCss);

    // then: expect only the own image to be unpublished
    assertPublishedTimes(Map.of(
        CORE_IMG_FOR_PAGE_1, 1,
        CORE_IMG_FOR_PAGE_2, 1,
        GLOBAL_IMAGE, 0,
        GLOBAL_CSS_CLIENTLIB, 1,
        GLOBAL_JS_CLIENTLIB, 1
    ));

    assertUnpublishedTimes(Map.of(
        CORE_IMG_FOR_PAGE_1, 1,
        CORE_IMG_FOR_PAGE_2, 0,
        GLOBAL_IMAGE, 0,
        GLOBAL_CSS_CLIENTLIB, 0,
        GLOBAL_JS_CLIENTLIB, 0
    ));

    assertResourcesCurrentlyOnStreamX(page1WithImagesAndCss, page2WithImagesAndJs, CORE_IMG_FOR_PAGE_2, GLOBAL_CSS_CLIENTLIB, GLOBAL_JS_CLIENTLIB);

    verifyStateOfPublishedResourcesData(
        Map.of(
            PAGE_2, Set.of(CORE_IMG_FOR_PAGE_2, GLOBAL_JS_CLIENTLIB)
        ),
        Set.of(
            CORE_IMG_FOR_PAGE_2,
            GLOBAL_JS_CLIENTLIB,
            GLOBAL_CSS_CLIENTLIB
        )
    );
  }

  @Test
  void unpublishingPageShouldNotRemoveDataForOtherPublishedPages() throws Exception {
    // given
    String page1 = registerResource("/content/my-site/a/b/c", generateHtmlPageContent(GLOBAL_JS_CLIENTLIB));
    String page2 = registerResource("/content/my-site/a/b", generateHtmlPageContent(GLOBAL_JS_CLIENTLIB));
    String page3 = registerResource("/content/my-site/a", generateHtmlPageContent(GLOBAL_JS_CLIENTLIB));

    // when 1
    publishPages(page1, page2, page3);

    // then
    assertResourcesCurrentlyOnStreamX(page1, page2, page3, GLOBAL_JS_CLIENTLIB);

    verifyStateOfPublishedResourcesData(
        Map.of(
            page3, Set.of(GLOBAL_JS_CLIENTLIB),
            page2, Set.of(GLOBAL_JS_CLIENTLIB),
            page1, Set.of(GLOBAL_JS_CLIENTLIB)
        ),
        Set.of(
            GLOBAL_JS_CLIENTLIB
        )
    );

    // when 2:
    unpublishPage(page3);

    // then
    assertResourcesCurrentlyOnStreamX(page1, page2, GLOBAL_JS_CLIENTLIB);

    verifyStateOfPublishedResourcesData(
        Map.of(
            page2, Set.of(GLOBAL_JS_CLIENTLIB),
            page1, Set.of(GLOBAL_JS_CLIENTLIB)
        ),
        Set.of(
            GLOBAL_JS_CLIENTLIB
        )
    );

    // when 3: unpublish all remaining pages to additionally test cleaning up the JCR trees
    unpublishPage(page1);
    unpublishPage(page2);

    // then
    assertResourcesCurrentlyOnStreamX(GLOBAL_JS_CLIENTLIB);

    verifyStateOfPublishedResourcesData(
        Collections.emptyMap(),
        Set.of(
            GLOBAL_JS_CLIENTLIB
        )
    );
  }

  @Test
  void unpublishingPageShouldNotRemoveDataForRelatedResourcesPublishedWithOtherPage() throws Exception {
    // given
    String image1 = "/content/my-site/a/b/c/image.coreimg.1.jpg";
    String image2 = "/content/my-site/a/b/image.coreimg.1.jpg";
    String image3 = "/content/my-site/a/image.coreimg.1.jpg";
    String page1 = registerResource("/content/my-site/a/b/c", generateHtmlPageContent(image1));
    String page2 = registerResource("/content/my-site/a/b", generateHtmlPageContent(image2));
    String page3 = registerResource("/content/my-site/a", generateHtmlPageContent(image3));

    // when 1
    publishPages(page1, page2, page3);

    // then
    assertResourcesCurrentlyOnStreamX(page1, page2, page3, image1, image2, image3);

    verifyStateOfPublishedResourcesData(
        Map.of(
            page3, Set.of(image3),
            page2, Set.of(image2),
            page1, Set.of(image1)
        ),
        Set.of(
            image3,
            image2,
            image1
        )
    );

    // when 2:
    unpublishPage(page3);

    // then
    assertResourcesCurrentlyOnStreamX(page1, page2, image1, image2);

    verifyStateOfPublishedResourcesData(
        Map.of(
            page2, Set.of(image2),
            page1, Set.of(image1)
        ),
        Set.of(
            image2,
            image1
        )
    );
  }

  @Test
  void republishingPageWithRemovedRelatedResource_ShouldNotRemoveDataOfNestedResources() throws Exception {
    // given
    String image1 = "/content/my-site/a/b/image.coreimg.1.jpg";
    String image2 = "/content/my-site/a/b/c/image.coreimg.1.jpg";
    String page2 = registerResource("/content/my-site/a/b/c", generateHtmlPageContent(image2));
    String page1 = registerResource("/content/my-site/a/b", generateHtmlPageContent(image1));

    // when 1:
    publishPages(page1, page2);

    // then
    assertResourcesCurrentlyOnStreamX(page1, page2, image1, image2);

    verifyStateOfPublishedResourcesData(
        Map.of(
            page1, Set.of(image1),
            page2, Set.of(image2)
        ),
        Set.of(
            image1,
            image2
        )
    );

    // when 2:
    editResourceToRemoveAllRelatedResources(page1);
    publishPages(page1);

    // then: expecting image1 to be gone from StreamX
    assertResourcesCurrentlyOnStreamX(page2, page1, image2);

    verifyStateOfPublishedResourcesData(
        Map.of(
            page2, Set.of(image2)
        ),
        Set.of(
            image2
        )
    );
  }

  @Test
  void shouldHandlePageWith1000RelatedResources() throws Exception {
    // given
    String imagePathFormat = PAGE_1 + "/images/image.coreimg.jpg/%d/bar.jpg";
    String page1WithImagesAndCss = registerResource(
        PAGE_1,
        generateHtmlPageContent(
            IntStream.rangeClosed(1, 1000).boxed()
                .map(i -> String.format(imagePathFormat, i))
                .toArray(String[]::new)
        )
    );

    // when
    publishPage(page1WithImagesAndCss);
    unpublishPage(page1WithImagesAndCss);

    // then
    assertNoResourcesCurrentlyOnStreamX();

    // and
    System.out.println("Total processing time by Publication Service (in millis): " + publicationServiceProcessingTotalTimeMillis);
    assertThat(publicationServiceProcessingTotalTimeMillis).isLessThan(1000);
  }

  @Test
  void shouldPublishNestedRelatedResource() throws Exception {
    // given
    MockOsgi.modified(selector, slingContext.bundleContext(), Map.of(
        "references.search-regexes", new String[]{"(/etc.clientlibs/.+\\.css)", "url\\(([^\\)]+)\\)"},
        "related-resource.processable-path.regex", ".*\\.css$",
        "references.exclude-from-result.regex", ""
    ));

    // and
    String cssResourcePath = "/etc.clientlibs/styles.css";
    String nestedCssResourcePath = "/etc.clientlibs/nested/styles.css";

    registerResource(
        cssResourcePath,
        "@import url(" + nestedCssResourcePath + ")"
    );

    registerResource(
        nestedCssResourcePath,
        "any content"
    );

    String page = registerResource(
        PAGE_1,
        generateHtmlPageContent(cssResourcePath)
    );

    // when:
    publishPage(page);

    // then:
    assertPublishedTimes(Map.of(
        page, 1,
        cssResourcePath, 1,
        nestedCssResourcePath, 1
    ));

    assertResourcesCurrentlyOnStreamX(page, cssResourcePath, nestedCssResourcePath);
  }

  @Test
  void shouldHandleBigNumberOfPagesWithBigNumberOfRelatedResources() throws Exception {
    // given
    final int N = 100; // pages and images max count
    final String imagePathFormat = "/content/my-site/page-%d/images/image.coreimg.jpg/%d/foo.jpg";

    Map<Integer, String> pagePaths = IntStream
        .rangeClosed(1, N).boxed()
        .collect(Collectors.toMap(
            Function.identity(),
            pageNumber -> "/content/my-site/page-" + pageNumber
        ));

    // when: publish all the pages, each with references to images that have numbers from 1 to the page number (inclusive)
    pagePaths.forEach((pageNumber, pagePath) ->
        registerResource(pagePath,
            generateHtmlPageContent(
                IntStream.rangeClosed(1, pageNumber).boxed()
                    .map(imageNumber -> String.format(imagePathFormat, pageNumber, imageNumber))
                    .toArray(String[]::new)
            )
        )
    );
    publishPages(pagePaths.values());

    // then:
    Set<String> expectedResourcesOnStreamX = new TreeSet<>(pagePaths.values());
    IntStream.rangeClosed(1, N).boxed()
            .flatMap(pageNumber -> IntStream.rangeClosed(1, pageNumber).boxed()
                .map(imageNumber -> String.format(imagePathFormat, pageNumber, imageNumber))
            ).forEach(expectedResourcesOnStreamX::add);
    assertResourcesCurrentlyOnStreamX(expectedResourcesOnStreamX);

    // when: unpublish pages from 10 to N
    unpublishPages(pagePaths.entrySet().stream()
        .filter(entry -> entry.getKey() >= 10)
        .map(Entry::getValue)
        .collect(Collectors.toList())
    );

    // then: expect pages 1-9 to stay on StreamX (along with their images)
    expectedResourcesOnStreamX.clear();
    pagePaths.entrySet().stream()
        .filter(entry -> entry.getKey() < 10)
        .map(Entry::getValue)
        .forEach(expectedResourcesOnStreamX::add);
    IntStream.rangeClosed(1, 9).boxed()
        .flatMap(pageNumber -> IntStream.rangeClosed(1, pageNumber).boxed()
            .map(imageNumber -> String.format(imagePathFormat, pageNumber, imageNumber))
        ).forEach(expectedResourcesOnStreamX::add);
    assertResourcesCurrentlyOnStreamX(expectedResourcesOnStreamX);

    // final assertion: make sure publication (along with the JCR operations) are efficient enough
    System.out.println("Total processing time by Publication Service (in millis): " + publicationServiceProcessingTotalTimeMillis);
    assertThat(publicationServiceProcessingTotalTimeMillis).isLessThan(3000);
  }

  @Test
  void shouldNotSaveChangesInRelatedResourcesJcrTree_IfErrorWhileAddingPublicationToQueue() throws Exception {
    // given
    String page1 = registerResource(PAGE_1, generateHtmlPageContent(CORE_IMG_FOR_PAGE_1));
    String page2 = registerResource(PAGE_2, generateHtmlPageContent(CORE_IMG_FOR_PAGE_2));

    // and
    configureErrorWhileCreatingPublicationJob(CORE_IMG_FOR_PAGE_2, PublicationAction.PUBLISH);

    // when
    publishPages(page1, page2);

    // then
    verify(resultBuilderMock).message(
        "Error while processing job: Can't submit publication jobs for related resources. "
        + "Publication job could not be created by JobManager for " + CORE_IMG_FOR_PAGE_2);

    // and: expect resources that reached the queue - to be published to StreamX
    assertResourcesCurrentlyOnStreamX(page1, page2, CORE_IMG_FOR_PAGE_1);

    // and: in case of any error adding publication job - expect the JCR tree to be unchanged
    verifyStateOfPublishedResourcesData(
        Collections.emptyMap(),
        Collections.emptySet()
    );
  }

  @Test
  void shouldNotSaveChangesInRelatedResourcesJcrTree_IfErrorWhileAddingUnpublicationToQueue() throws Exception {
    // given
    String page = registerResource(PAGE_1, generateHtmlPageContent(CORE_IMG_FOR_PAGE_1));

    // and
    configureErrorWhileCreatingPublicationJob(CORE_IMG_FOR_PAGE_1, PublicationAction.UNPUBLISH);

    // when
    publishPages(page);

    // then
    assertResourcesCurrentlyOnStreamX(page, CORE_IMG_FOR_PAGE_1);

    // and
    verifyStateOfPublishedResourcesData(
        Map.of(
            PAGE_1, Set.of(CORE_IMG_FOR_PAGE_1)
        ),
        Set.of(
            CORE_IMG_FOR_PAGE_1
        )
    );

    // when 2: simulate removing image from page content, to trigger unpublishing the image
    editResourceToRemoveAllRelatedResources(page);
    publishPages(page);

    // then
    verify(resultBuilderMock).message(
        "Error while processing job: Can't submit publication jobs for related resources. "
        + "Publication job could not be created by JobManager for " + CORE_IMG_FOR_PAGE_1);

    // and
    assertResourcesCurrentlyOnStreamX(page, CORE_IMG_FOR_PAGE_1);

    // and: in case of any error adding publication job - expect the JCR tree to be unchanged
    verifyStateOfPublishedResourcesData(
        Map.of(
            PAGE_1, Set.of(CORE_IMG_FOR_PAGE_1)
        ),
        Set.of(
            CORE_IMG_FOR_PAGE_1
        )
    );
  }

  private void configureErrorWhileCreatingPublicationJob(String resourcePath, PublicationAction action) {
    doReturn(null)
        .when(jobManager)
        .addJob(
            eq(PublicationJobExecutor.JOB_TOPIC),
            argThat(properties ->
                properties.get(PN_STREAMX_PUBLICATION_ACTION).equals(action.toString())
                && properties.get(PN_STREAMX_PUBLICATION_PATH).equals(resourcePath))
        );
  }

  private String registerResource(String resourcePath, String content) {
    allTestResources.put(resourcePath, content);
    loadPageToSlingContext(resourcePath);
    return resourcePath;
  }

  private void editResourceToRemoveAllRelatedResources(String resourcePath) {
    allTestResources.put(resourcePath, "<html />");
  }

  private void loadPageToSlingContext(String pageResourcePath) {
    if (resourceResolver.getResource(pageResourcePath) == null) {
      slingContext.load().json(PAGE_JSON_RESOURCE_FILE_PATH, pageResourcePath);
    }
  }

  private static String generateHtmlPageContent(String... relatedResourcePathsToInclude) {
    return Arrays.stream(relatedResourcePathsToInclude)
        .map(path -> "<include path='" + path + "' />") // any include tag, doesn't have to exist in HTML language
        .collect(Collectors.joining("\n", "<html><body>", "</body></html>"));
  }

  private void verifyStateOfPublishedResourcesData(
      Map<String, Set<String>> expectedParentAndRelatedResourcesInMainTree,
      Set<String> expectedRelatedResourcePathsInInversedTree) throws RepositoryException {
    verifyMainTree(expectedParentAndRelatedResourcesInMainTree);
    verifyInversedTree(expectedRelatedResourcePathsInInversedTree);
  }

  private void verifyMainTree(Map<String, Set<String>> expectedParentAndRelatedResources) throws RepositoryException {
    String baseNodePath = PublishedRelatedResourcesManager.BASE_NODE_PATH;

    // given
    Map<String, Map<String, Set<String>>> expectedTreeNodes = expectedParentAndRelatedResources.entrySet()
        .stream()
        .collect(Collectors.toMap(
            entry -> baseNodePath + entry.getKey(),
            entry -> Map.of("relatedResources", entry.getValue())
        ));
    addParentNodes(expectedTreeNodes, expectedParentAndRelatedResources.keySet(), baseNodePath);

    // when
    Map<String, Map<String, Set<String>>> actualTreeNodes = JcrTreeReader.getNestedNodes(baseNodePath, resourceResolver);

    // then
    assertThat(actualTreeNodes).containsExactlyInAnyOrderEntriesOf(expectedTreeNodes);
  }

  private void verifyInversedTree(Set<String> expectedRelatedResourcePaths) throws RepositoryException {
    String baseNodePath = PublishedRelatedResourcesInversedTreeManager.BASE_NODE_PATH;

    // given
    Map<String, Map<String, Set<String>>> expectedTreeNodes = expectedRelatedResourcePaths
        .stream()
        .collect(Collectors.toMap(
            path -> baseNodePath + path,
            path -> Collections.emptyMap()
        ));
    addParentNodes(expectedTreeNodes, expectedRelatedResourcePaths, baseNodePath);

    // when
    Map<String, Map<String, Set<String>>> actualTreeNodes = JcrTreeReader.getNestedNodes(baseNodePath, resourceResolver);

    // then
    assertThat(actualTreeNodes).containsExactlyInAnyOrderEntriesOf(expectedTreeNodes);
  }

  private static void addParentNodes(Map<String, Map<String, Set<String>>> nodesMap, Set<String> sourceNodes, String baseNodePath) {
    for (String relativePath : sourceNodes) {
      while (!relativePath.isEmpty()) {
        relativePath = StringUtils.substringBeforeLast(relativePath, "/");
        String absolutePath = baseNodePath + relativePath;
        if (!nodesMap.containsKey(absolutePath)) {
          nodesMap.put(absolutePath, Collections.emptyMap());
        }
      }
    }
  }

  private void publishPage(String resourcePath) throws Exception {
    publishPages(List.of(resourcePath));
  }

  private void publishPages(String... resourcePaths) throws Exception {
    publishPages(Arrays.asList(resourcePaths));
  }

  private void publishPages(Collection<String> resourcePaths) throws Exception {
    ingestPages(resourcePaths, PublicationAction.PUBLISH);
  }

  private void unpublishPage(String resourcePath) throws Exception {
    unpublishPages(List.of(resourcePath));
  }

  private void unpublishPages(Collection<String> resourcePaths) throws Exception {
    ingestPages(resourcePaths, PublicationAction.UNPUBLISH);
  }

  private void ingestPages(Collection<String> resourcePaths, PublicationAction action) throws Exception {
    List<ResourceInfo> resourcesToIngest = resourcePaths.stream().map(PageResourceInfo::new).collect(Collectors.toList());
    if (action == PublicationAction.PUBLISH) {
      publicationService.publish(resourcesToIngest);
    } else if (action == PublicationAction.UNPUBLISH) {
      publicationService.unpublish(resourcesToIngest);
    }

    long startTimeNanos = System.nanoTime();
    ingestionTriggerJobExecutor.process(jobManager.popLastJob(), jobExecutionContext);
    long elapsedTimeNanos = System.nanoTime() - startTimeNanos;
    publicationServiceProcessingTotalTimeMillis += Duration.ofNanos(elapsedTimeNanos).toMillis();
  }

  private void assertPublishedTimes(Map<String, Integer> resourcePathsAndTimes) {
    resourcePathsAndTimes.forEach(this::assertPublishedTimes);
  }

  private void assertPublishedTimes(String resourcePath, int times) {
    assertIngestedTimes(resourcePath, times, PublicationAction.PUBLISH);
  }

  private void assertUnpublishedTimes(Map<String, Integer> resourcePathsAndTimes) {
    resourcePathsAndTimes.forEach(this::assertUnpublishedTimes);
  }

  private void assertUnpublishedTimes(String resourcePath, int times) {
    assertIngestedTimes(resourcePath, times, PublicationAction.UNPUBLISH);
  }

  private void assertNoUnpublishes() {
    assertThat(
        jobManager.getJobQueue().stream()
            .filter(job -> job.getTopic().equals("dev/streamx/publications"))
            .filter(job -> job.hasProperty(PN_STREAMX_PUBLICATION_ACTION, PublicationAction.UNPUBLISH))
    ).isEmpty();
  }

  private void assertIngestedTimes(String resourcePath, int times, PublicationAction action) {
    long actualIngestedTimes = jobManager.getJobQueue().stream()
        .filter(job -> job.getTopic().equals("dev/streamx/publications"))
        .filter(job -> job.hasProperty(PN_STREAMX_PUBLICATION_ACTION, action.name()))
        .filter(job -> job.hasProperty(PN_STREAMX_PUBLICATION_PATH, resourcePath))
        .count();
    assertThat(actualIngestedTimes).describedAs(resourcePath).isEqualTo(times);
  }

  private void assertNoResourcesCurrentlyOnStreamX() {
    assertResourcesCurrentlyOnStreamX();
  }

  private void assertResourcesCurrentlyOnStreamX(String... expectedResourcePaths) {
    assertResourcesCurrentlyOnStreamX(new TreeSet<>(Set.of(expectedResourcePaths)));
  }

  private void assertResourcesCurrentlyOnStreamX(Set<String> expectedResourcePaths) {
    Set<String> actualResourcePaths = new TreeSet<>();
    for (var job : jobManager.getJobQueue()) {
      String action = job.getProperty(PN_STREAMX_PUBLICATION_ACTION, String.class);
      String resourcePath = job.getProperty(PN_STREAMX_PUBLICATION_PATH, String.class);
      if (action.equals(PublicationAction.PUBLISH.name())) {
        actualResourcePaths.add(resourcePath);
      } else if (action.equals(PublicationAction.UNPUBLISH.name())) {
        actualResourcePaths.remove(resourcePath);
      }
    }

    assertThat(actualResourcePaths).containsExactlyInAnyOrderElementsOf(expectedResourcePaths);
  }
}
