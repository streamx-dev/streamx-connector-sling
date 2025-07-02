package dev.streamx.sling.connector.selectors.content;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.test.util.PageResourceInfo;
import dev.streamx.sling.connector.test.util.ResourceContentRelatedResourcesSelectorConfigImpl;
import dev.streamx.sling.connector.test.util.ResourceResolverMocks;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.io.FileUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlingContextExtension.class)
class ResourceContentRelatedResourcesSelectorRecursiveSearchTest {

  private static final File TEST_RESOURCES_ROOT_DIR = new File("src/test/resources/page-with-nested-references");
  private static final String MAIN_PAGE_RESOURCE = "/content/my-site/us/en/main-page";
  private static final String MAIN_PAGE_HTML_PATH = "/content/my-site/us/en/main-page.html";

  // key: AEM-style path, value: resource content
  private static final Map<String, String> testResourceFiles = FileUtils
      .listFiles(TEST_RESOURCES_ROOT_DIR, null, true)
      .stream()
      .filter(file -> !file.isHidden()) // skip hidden files like .DS_Store
      .collect(Collectors.toMap(
          file -> "/" + TEST_RESOURCES_ROOT_DIR.toPath().relativize(file.toPath()),
          file -> contentOf(file, UTF_8)
      ));

  private final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);
  private final ResourceResolver resourceResolverSpy = spy(context.resourceResolver());
  private final ResourceResolverFactory resourceResolverFactoryMock = mock(ResourceResolverFactory.class);

  private final SlingRequestProcessor basicRequestProcessor = (HttpServletRequest request, HttpServletResponse response, ResourceResolver resourceResolver) -> {
    String requestURI = request.getRequestURI();
    String content = Objects.requireNonNull(testResourceFiles.get(requestURI), requestURI);
    response.setContentType("text/html");
    response.getWriter().write(content);
  };

  @BeforeEach
  void setupResourceResolver() throws Exception {
    ResourceResolverMocks.configure(resourceResolverSpy, resourceResolverFactoryMock);
  }

  @Test
  void shouldFindRelatedResources_WhenSearchingRecursivelyInCssAndJsFiles() {
    shouldFindRelatedResources(
        ".*\\.(css|js)$",
        List.of(
            "/apps/my-site/clientlibs/react-app/main.chunk.js",
            "/apps/my-site/clientlibs/react-app/runtime~main.chunk.js",
            "/content/dam/images/bg-1.png",
            "/etc.clientlibs/my-site/assets/colors-3.css",
            "/etc.clientlibs/my-site/assets/config.json",
            "/etc.clientlibs/my-site/clientlibs/clientlib-base-1.css",
            "/etc.clientlibs/my-site/clientlibs/clientlib-base-1.js",
            "/etc.clientlibs/my-site/clientlibs/theme/colors-1.css",
            "/etc.clientlibs/my-site/clientlibs/theme/colors-2.css",
            "/etc.clientlibs/my-site/icons/icon-home.svg",
            "/etc.clientlibs/my-site/icons/icon-logo.svg",
            "/etc.clientlibs/my-site/icons/icon-sprite.svg"
        ),
        List.of(
            MAIN_PAGE_HTML_PATH,
            "/etc.clientlibs/my-site/assets/included-config.json"
        )
    );
  }

  @Test
  void shouldFindRelatedResources_WhenSearchingRecursivelyInCssFiles() {
    shouldFindRelatedResources(
        ".*\\.css$",
        List.of(
            "/content/dam/images/bg-1.png",
            "/etc.clientlibs/my-site/assets/colors-3.css",
            "/etc.clientlibs/my-site/clientlibs/clientlib-base-1.css",
            "/etc.clientlibs/my-site/clientlibs/clientlib-base-1.js",
            "/etc.clientlibs/my-site/clientlibs/theme/colors-1.css",
            "/etc.clientlibs/my-site/clientlibs/theme/colors-2.css",
            "/etc.clientlibs/my-site/icons/icon-home.svg",
            "/etc.clientlibs/my-site/icons/icon-logo.svg",
            "/etc.clientlibs/my-site/icons/icon-sprite.svg"
        ),
        List.of(
            MAIN_PAGE_HTML_PATH,
            "/apps/my-site/clientlibs/react-app/main.chunk.js",
            "/apps/my-site/clientlibs/react-app/runtime~main.chunk.js",
            "/etc.clientlibs/my-site/assets/config.json",
            "/etc.clientlibs/my-site/assets/included-config.json"
        )
    );
  }

  private void shouldFindRelatedResources(String relatedResourceProcessablePathRegex,
      List<String> expectedFoundResources, List<String> expectedNotFoundResources) {

    // given
    ResourceContentRelatedResourcesSelector relatedResourcesSelector = new ResourceContentRelatedResourcesSelector(
        new ResourceContentRelatedResourcesSelectorConfigImpl()
            .withReferencesSearchRegexes(
                "(/apps/[^\"']+)",
                "(/content/[^\"']+)",
                "(/etc\\.clientlibs[^\"']*)")
            .withResourcePathPostfixToAppend(".html")
            .withResourceRequiredPathRegex("^/content/my-site/us/en/.*")
            .withRelatedResourceProcessablePathRegex(relatedResourceProcessablePathRegex)
            .withResourceRequiredPrimaryNodeTypeRegex("cq:Page"),
        basicRequestProcessor,
        resourceResolverFactoryMock
    );

    // when
    ResourceInfo mainPageResource = new PageResourceInfo(MAIN_PAGE_RESOURCE);
    Collection<ResourceInfo> actualRelatedResources = relatedResourcesSelector.getRelatedResources(mainPageResource);

    // then
    assertThat(actualRelatedResources)
        .extracting(ResourceInfo::getPath)
        .containsExactlyElementsOf(expectedFoundResources);

    // and: should collect all test resources, except the ones provided in the expectedNotFoundResources list
    assertThat(actualRelatedResources)
        .extracting(ResourceInfo::getPath)
        .containsExactlyInAnyOrderElementsOf(
            SetUtils.difference(testResourceFiles.keySet(), Set.copyOf(expectedNotFoundResources))
        );
  }
}
