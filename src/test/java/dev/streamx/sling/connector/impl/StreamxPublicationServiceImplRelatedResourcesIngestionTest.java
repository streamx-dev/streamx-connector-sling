package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_ACTION;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PATH;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.handlers.resourcepath.AssetResourcePathPublicationHandler;
import dev.streamx.sling.connector.handlers.resourcepath.AssetResourcePathPublicationHandlerConfig;
import dev.streamx.sling.connector.selectors.content.ResourceContentRelatedResourcesSelector;
import dev.streamx.sling.connector.selectors.content.ResourceContentRelatedResourcesSelectorConfig;
import dev.streamx.sling.connector.test.util.PageResourceInfo;
import dev.streamx.sling.connector.test.util.ResourceContentRelatedResourcesSelectorConfigImpl;
import dev.streamx.sling.connector.test.util.ResourceMocks;
import dev.streamx.sling.connector.testing.handlers.PagePublicationHandler;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJobManager;
import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.jcr.Session;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.engine.SlingRequestProcessor;
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
import org.mockito.MockedStatic;

@ExtendWith(SlingContextExtension.class)
class StreamxPublicationServiceImplRelatedResourcesIngestionTest {

  private static final String PAGE_1 = "/content/my-site/en/us/page-1";
  private static final String PAGE_2 = "/content/my-site/en/us/page-2";

  private static final String CORE_IMG_FOR_PAGE_1 = "/content/my-site/en/us/page-1/_jcr_content/root/container/container/image.coreimg.jpg/11111/foo.jpg";
  private static final String CORE_IMG_FOR_PAGE_2 = "/content/my-site/en/us/page-2/_jcr_content/root/container/container/image.coreimg.jpg/22222/foo.jpg";

  private static final String GLOBAL_IMAGE = "/content/dam/bar.jpg";
  private static final String GLOBAL_CSS_CLIENTLIB = "/etc.clientlibs/clientlib-1.js";
  private static final String GLOBAL_JS_CLIENTLIB = "/etc.clientlibs/clientlib-1.css";

  private static final String PAGE_JSON_RESOURCE_FILE_PATH = "src/test/resources/page.json";

  private final SlingContext slingContext = new SlingContext(ResourceResolverType.JCR_OAK);
  private final ResourceResolver resourceResolver = spy(slingContext.resourceResolver());
  private final ResourceResolverFactory resourceResolverFactory = mock(ResourceResolverFactory.class);
  private final FakeJobManager jobManager = new FakeJobManager(Collections.emptyList());
  private final StreamxPublicationServiceImpl publicationService = new StreamxPublicationServiceImpl();
  private final JobExecutionContext jobExecutionContext = mock(JobExecutionContext.class);

  // resource path + content
  private final Map<String, String> allTestPages = new LinkedHashMap<>();

  private final String page1WithImagesAndCss = registerPage(
      PAGE_1,
      generateHtmlPageContent(CORE_IMG_FOR_PAGE_1, GLOBAL_IMAGE, GLOBAL_CSS_CLIENTLIB)
  );
  private final String page2WithImagesAndJs = registerPage(
      PAGE_2,
      generateHtmlPageContent(CORE_IMG_FOR_PAGE_2, GLOBAL_IMAGE, GLOBAL_JS_CLIENTLIB)
  );

  private final SlingRequestProcessor requestProcessor = (HttpServletRequest request, HttpServletResponse response, ResourceResolver resourceResolver) -> {
    String requestURI = request.getRequestURI();
    if (StringUtils.endsWithAny(requestURI, ".jpg", ".css", ".js")) {
      response.setContentType("application/octet-stream");
      response.getWriter().write("Content of image " + requestURI);
    } else {
      assertThat(allTestPages.keySet()).contains(requestURI);
      response.setContentType("text/html");
      response.getWriter().write(allTestPages.get(requestURI));
    }
  };

  private final ResourceContentRelatedResourcesSelectorConfig relatedResourcesConfig = new ResourceContentRelatedResourcesSelectorConfigImpl()
      .withReferencesSearchRegexes("(/content/.+coreimg.+\\.jpg)", "(/etc.clientlibs/.+\\.(css|js))")
      .withResourcePathPostfixToAppend(".html")
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
        .resolve(ArgumentMatchers.<String>argThat(path -> StringUtils.endsWithAny(path, ".jpg", ".css", ".js")));

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
    slingContext.registerService(PublicationHandler.class, new AssetResourcePathPublicationHandler(resourceResolverFactory, requestProcessor, assetResourcePathPublicationHandlerConfig));
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
  void shouldPublishPagesAndAllRelatedResourcesThatAreConfigureToBeFound_AndUnpublishOwnCoreImagesAlongWithPage() throws Exception {
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
            page1WithImagesAndCss, List.of(CORE_IMG_FOR_PAGE_1, GLOBAL_CSS_CLIENTLIB),
            page2WithImagesAndJs, List.of(CORE_IMG_FOR_PAGE_2, GLOBAL_JS_CLIENTLIB)
        ),
        List.of(
            CORE_IMG_FOR_PAGE_1, GLOBAL_CSS_CLIENTLIB, CORE_IMG_FOR_PAGE_2, GLOBAL_JS_CLIENTLIB
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
            page2WithImagesAndJs, List.of(CORE_IMG_FOR_PAGE_2, GLOBAL_JS_CLIENTLIB)
        ),
        List.of(
            GLOBAL_CSS_CLIENTLIB, CORE_IMG_FOR_PAGE_2, GLOBAL_JS_CLIENTLIB
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
        List.of(
            GLOBAL_CSS_CLIENTLIB, GLOBAL_JS_CLIENTLIB
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
            page2WithImagesAndJs, List.of(CORE_IMG_FOR_PAGE_2, GLOBAL_JS_CLIENTLIB)
        ),
        List.of(
            CORE_IMG_FOR_PAGE_2, GLOBAL_CSS_CLIENTLIB, GLOBAL_JS_CLIENTLIB
        )
    );
  }

  @Test
  void shouldUnpublishUnreferencedOwnImageWhenPublishingEditedPage() throws Exception {
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
            page1WithImagesAndCss, List.of(CORE_IMG_FOR_PAGE_1, GLOBAL_CSS_CLIENTLIB),
            page2WithImagesAndJs, List.of(CORE_IMG_FOR_PAGE_2, GLOBAL_JS_CLIENTLIB)
        ),
        List.of(
            CORE_IMG_FOR_PAGE_1, GLOBAL_CSS_CLIENTLIB, CORE_IMG_FOR_PAGE_2, GLOBAL_JS_CLIENTLIB
        )
    );

    // ---------
    // when 2: edit the page to remove all related resources from its content
    editPage(page1WithImagesAndCss, "<html />");
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
            page1WithImagesAndCss, Collections.emptyList(),
            page2WithImagesAndJs, List.of(CORE_IMG_FOR_PAGE_2, GLOBAL_JS_CLIENTLIB)
        ),
        List.of(
            GLOBAL_CSS_CLIENTLIB, CORE_IMG_FOR_PAGE_2, GLOBAL_JS_CLIENTLIB
        )
    );
  }

  @Test
  void unpublishingPageShouldNotRemoveDataForOtherPublishedPages() throws Exception {
    // given
    String page1 = registerPage("/content/my-site/a/b/c", generateHtmlPageContent(GLOBAL_JS_CLIENTLIB));
    String page2 = registerPage("/content/my-site/a/b", generateHtmlPageContent(GLOBAL_JS_CLIENTLIB));
    String page3 = registerPage("/content/my-site/a", generateHtmlPageContent(GLOBAL_JS_CLIENTLIB));

    // when 1
    publishPages(page1, page2, page3);

    // then
    assertResourcesCurrentlyOnStreamX(page1, page2, page3, GLOBAL_JS_CLIENTLIB);

    verifyStateOfPublishedResourcesData(
        Map.of(
            page1, List.of(GLOBAL_JS_CLIENTLIB),
            page2, List.of(GLOBAL_JS_CLIENTLIB),
            page3, List.of(GLOBAL_JS_CLIENTLIB)
        ),
        List.of(
            GLOBAL_JS_CLIENTLIB
        )
    );

    // when 2:
    unpublishPage(page3);

    // then
    assertResourcesCurrentlyOnStreamX(page1, page2, GLOBAL_JS_CLIENTLIB);

    verifyStateOfPublishedResourcesData(
        Map.of(
            page1, List.of(GLOBAL_JS_CLIENTLIB),
            page2, List.of(GLOBAL_JS_CLIENTLIB)
        ),
        List.of(
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
    String page1 = registerPage("/content/my-site/a/b/c", generateHtmlPageContent(image1));
    String page2 = registerPage("/content/my-site/a/b", generateHtmlPageContent(image2));
    String page3 = registerPage("/content/my-site/a", generateHtmlPageContent(image3));

    // when 1
    publishPages(page1, page2, page3);

    // then
    assertResourcesCurrentlyOnStreamX(page1, page2, page3, image1, image2, image3);

    verifyStateOfPublishedResourcesData(
        Map.of(
            page1, List.of(image1),
            page2, List.of(image2),
            page3, List.of(image3)
        ),
        List.of(
            image1, image2, image3
        )
    );

    // when 2:
    unpublishPage(page3);

    // then
    assertResourcesCurrentlyOnStreamX(page1, page2, image1, image2);

    verifyStateOfPublishedResourcesData(
        Map.of(
            page1, List.of(image1),
            page2, List.of(image2)
        ),
        List.of(
            image1, image2
        )
    );
  }

  @Test
  void shouldHandlePageWith1000RelatedResources() throws Exception {
    // given
    String imagePathFormat = page1WithImagesAndCss
                             + "/_jcr_content/root/container/container/image.coreimg.jpg/%d/bar.jpg";

    editPage(page1WithImagesAndCss, generateHtmlPageContent(
        IntStream.rangeClosed(1, 1000).boxed()
            .map(i -> String.format(imagePathFormat, i))
            .toArray(String[]::new)
    ));

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
  void shouldHandleBigNumberOfPagesWithBigNumberOfRelatedResources() throws Exception {
    // given
    final int N = 100; // pages and images max count
    final String imagePathFormat = "/content/my-site/page-%d/_jcr_content/root/container/container/image.coreimg.jpg/%d/foo.jpg";

    Map<Integer, String> pagePaths = IntStream
        .rangeClosed(1, N).boxed()
        .collect(Collectors.toMap(
            Function.identity(),
            pageNumber -> "/content/my-site/page-" + pageNumber
        ));

    // when: publish all the pages, each with references to images that have numbers from 1 to the page number (inclusive)
    pagePaths.forEach((pageNumber, pagePath) ->
        registerPage(pagePath,
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
    assertThat(publicationServiceProcessingTotalTimeMillis).isLessThan(2500);
  }

  @Test
  void shouldCallPublishedRelatedResourcesTreeModifyMethodsOnlyOncePerProcessingRequest() throws Exception {
    try (MockedStatic<PublishedRelatedResourcesManager> treeManager = mockStatic(PublishedRelatedResourcesManager.class, CALLS_REAL_METHODS)) {
      // given
      Session sessionSpy = spy(requireNonNull(resourceResolver.adaptTo(Session.class)));
      treeManager.when(() -> PublishedRelatedResourcesManager.getSession(any())).thenReturn(sessionSpy);

      // when
      publishPages(List.of(page1WithImagesAndCss, page2WithImagesAndJs, page1WithImagesAndCss));
      unpublishPages(List.of(page2WithImagesAndJs, page1WithImagesAndCss, page1WithImagesAndCss));

      // then
      treeManager.verify(() -> PublishedRelatedResourcesManager.updatePublishedResourcesData(any(), any()), times(1));
      treeManager.verify(() -> PublishedRelatedResourcesManager.removePublishedResourcesData(any(), any()), times(1));

      // and
      verify(sessionSpy, times(2)).save();
    }
  }

  private String registerPage(String pageResourcePath, String content) {
    allTestPages.put(pageResourcePath + ".html", content);
    loadPageToSlingContext(pageResourcePath);
    return pageResourcePath;
  }

  private void editPage(String pageResourcePath, String content) {
    allTestPages.put(pageResourcePath + ".html", content);
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
      Map<String, List<String>> expectedParentAndRelatedResourcePathsInMainTree,
      List<String> expectedRelatedResourcePathsInInversedTree) {

    // 1. Verify tree by parent resource path
    Map<String, List<String>> actualParentAndRelatedResourcePaths = loadDataFromReferencedRelatedResourcesTree();

    assertThat(actualParentAndRelatedResourcePaths)
        .containsExactlyInAnyOrderEntriesOf(expectedParentAndRelatedResourcePathsInMainTree);

    // 2. Verify tree by related resource path
    List<String> actualRelatedResourcePaths = loadDataFromRelatedResourcesTree();

    assertThat(actualRelatedResourcePaths)
        .hasSameSizeAs(expectedRelatedResourcePathsInInversedTree)
        .containsExactlyInAnyOrderElementsOf(expectedRelatedResourcePathsInInversedTree);
  }

  private Map<String, List<String>> loadDataFromReferencedRelatedResourcesTree() {
    String mainTreeNodePath = "/var/streamx/connector/sling/referenced-related-resources";
    String nameOfNodeWithChildren = "relatedResources";
    Map<String, List<String>> resourcesMap = new HashMap<>();
    Resource mainTreeNode = resourceResolver.getResource(mainTreeNodePath);
    for (Resource nodeWithChildren : getNodesThatContainProperty(mainTreeNode, nameOfNodeWithChildren)) {
      List<String> childPaths = Arrays.asList(nodeWithChildren.getValueMap().get(nameOfNodeWithChildren, new String[0]));
      String parentRelativePath = StringUtils.substringAfter(nodeWithChildren.getPath(), mainTreeNodePath);
      resourcesMap.put(parentRelativePath, childPaths);
    }
    return resourcesMap;
  }

  private List<Resource> getNodesThatContainProperty(Resource rootNode, String property) {
    List<Resource> result = new ArrayList<>();
    getNodesThatContainProperty(rootNode, property, result);
    return result;
  }

  private void getNodesThatContainProperty(Resource node, String property, List<Resource> result) {
    if (node.getValueMap().containsKey(property)) {
      result.add(node);
    }

    for (Resource child : node.getChildren()) {
      getNodesThatContainProperty(child, property, result);
    }
  }

  private List<String> loadDataFromRelatedResourcesTree() {
    String mainTreeNodePath = "/var/streamx/connector/sling/related-resources";
    Resource mainTreeNode = resourceResolver.getResource(mainTreeNodePath);
    List<Resource> leafNodes = getLeafNodes(mainTreeNode);
    return leafNodes.stream()
        .map(leafNode -> StringUtils.substringAfter(leafNode.getPath(), mainTreeNodePath))
        .collect(Collectors.toList());
  }

  private List<Resource> getLeafNodes(Resource rootNode) {
    List<Resource> result = new ArrayList<>();
    getLeafNodes(rootNode, result);
    return result;
  }

  private void getLeafNodes(Resource node, List<Resource> result) {
    if (node.getResourceType().equals("nt:unstructured")) {
      result.add(node);
    }

    for (Resource child : node.getChildren()) {
      getLeafNodes(child, result);
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
    publicationService.process(jobManager.popLastJob(), jobExecutionContext);
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
            .filter(job -> job.hasProperty(PN_STREAMX_ACTION, PublicationAction.UNPUBLISH))
    ).isEmpty();
  }

  private void assertIngestedTimes(String resourcePath, int times, PublicationAction action) {
    long actualIngestedTimes = jobManager.getJobQueue().stream()
        .filter(job -> job.getTopic().equals("dev/streamx/publications"))
        .filter(job -> job.hasProperty(PN_STREAMX_ACTION, action.name()))
        .filter(job -> job.hasProperty(PN_STREAMX_PATH, resourcePath))
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
