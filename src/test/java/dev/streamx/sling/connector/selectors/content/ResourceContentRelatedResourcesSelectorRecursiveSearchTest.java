package dev.streamx.sling.connector.selectors.content;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.test.util.ResourceMocks;
import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
import org.mockito.ArgumentMatchers;

@ExtendWith(SlingContextExtension.class)
class ResourceContentRelatedResourcesSelectorRecursiveSearchTest {

  private static final File TEST_RESOURCES_ROOT_DIR = new File("src/test/resources/page-with-nested-references");
  private static final String MAIN_PAGE_RESOURCE = "/content/my-site/us/en/main-page";

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
  private final ResourceResolverFactory resourceResolverFactoryMock = mock(ResourceResolverFactory.class);

  private final SlingRequestProcessor basicRequestProcessor = (HttpServletRequest request, HttpServletResponse response, ResourceResolver resourceResolver) -> {
    String requestURI = request.getRequestURI();
    String content = Objects.requireNonNull(testResourceFiles.get(requestURI), requestURI);
    response.setContentType("text/html");
    response.getWriter().write(content);
  };

  @BeforeEach
  void setupResourceResolver() throws Exception {
    ResourceResolver spyResolver = spy(context.resourceResolver());

    doReturn(ResourceMocks.createPageResourceMock())
        .when(spyResolver)
        .resolve(ArgumentMatchers.<String>argThat(path -> path.endsWith(".html") || path.equals(MAIN_PAGE_RESOURCE)));

    doReturn(ResourceMocks.createAssetResourceMock())
        .when(spyResolver)
        .resolve(ArgumentMatchers.<String>argThat(path -> !path.endsWith(".html") && !path.equals(MAIN_PAGE_RESOURCE)));

    doNothing().when(spyResolver).close();
    doReturn(spyResolver).when(resourceResolverFactoryMock).getAdministrativeResourceResolver(null);
  }

  @Test
  void shouldFindRelatedResources() {
    // given
    ResourceContentRelatedResourcesSelector relatedResourcesSelector = new ResourceContentRelatedResourcesSelector(
        new ResourceContentRelatedResourcesSelectorConfig() {

          @Override
          public Class<? extends Annotation> annotationType() {
            return ResourceContentRelatedResourcesSelectorConfig.class;
          }

          @Override
          public String[] references_search$_$regexes() {
            return new String[]{
                "(/apps/[^\"']+)",
                "(/content/[^\"']+)",
                "(/etc\\.clientlibs[^\"']*)"
            };
          }

          @Override
          public String references_exclude$_$from$_$result_regex() {
            return "";
          }

          @Override
          public String resource$_$path_postfix$_$to$_$append() {
            return ".html";
          }

          @Override
          public String resource_required$_$path_regex() {
            return "^/content/my-site/us/en/.*";
          }

          @Override
          public String related_resource_processable_path_regex() {
            return ".*\\.(html|css|js)$";
          }

          @Override
          public String resource_required$_$primary$_$node$_$type_regex() {
            return "cq:Page";
          }
        },
        basicRequestProcessor,
        resourceResolverFactoryMock
    );

    // when
    Collection<ResourceInfo> actualRelatedResources = relatedResourcesSelector.getRelatedResources(MAIN_PAGE_RESOURCE);

    // then
    assertThat(actualRelatedResources).containsExactly(
        new ResourceInfo("/apps/my-site/clientlibs/react-app/main.chunk.js"),
        new ResourceInfo("/apps/my-site/clientlibs/react-app/runtime~main.chunk.js"),
        new ResourceInfo("/content/dam/images/bg-1.png"),
        new ResourceInfo("/content/dam/images/bg-2.png"),
        new ResourceInfo("/content/my-site/us/en/main-page.html"),
        new ResourceInfo("/content/my-site/us/en/other-page.html"),
        new ResourceInfo("/etc.clientlibs/my-site/assets/config.json"),
        new ResourceInfo("/etc.clientlibs/my-site/clientlibs/clientlib-base-1.css"),
        new ResourceInfo("/etc.clientlibs/my-site/clientlibs/clientlib-base-1.js"),
        new ResourceInfo("/etc.clientlibs/my-site/clientlibs/clientlib-base-2.css"),
        new ResourceInfo("/etc.clientlibs/my-site/clientlibs/clientlib-base-2.js"),
        new ResourceInfo("/etc.clientlibs/my-site/clientlibs/theme/colors-1.css"),
        new ResourceInfo("/etc.clientlibs/my-site/clientlibs/theme/colors-2.css"),
        new ResourceInfo("/etc.clientlibs/my-site/icons/icon-sprite.svg")
    );

    // and: should collect all test resources
    assertThat(actualRelatedResources)
        .extracting(ResourceInfo::getPath)
        .containsExactlyInAnyOrderElementsOf(testResourceFiles.keySet());
  }
}
