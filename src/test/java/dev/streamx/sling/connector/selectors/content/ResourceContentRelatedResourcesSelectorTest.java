package dev.streamx.sling.connector.selectors.content;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import dev.streamx.sling.connector.ResourceInfo;
import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.jcr.Node;
import javax.jcr.nodetype.NodeType;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;

@ExtendWith(SlingContextExtension.class)
class ResourceContentRelatedResourcesSelectorTest {

  private static final String MAIN_FOLDER_RESOURCE = "/content/firsthops/us/en";
  private static final String MAIN_PAGE_RESOURCE = "/content/firsthops/us/en.html";
  private static final File samplePageFile = new File("src/test/resources/sample-page.html");

  private final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);
  private final ResourceResolverFactory resourceResolverFactoryMock = mock(ResourceResolverFactory.class);

  private final SlingRequestProcessor basicRequestProcessor = (HttpServletRequest request, HttpServletResponse response, ResourceResolver resourceResolver) -> {
    String requestURI = request.getRequestURI();
    response.setContentType("text/html");
    response.getWriter().write(
        requestURI.equals(MAIN_PAGE_RESOURCE)
            ? FileUtils.readFileToString(samplePageFile, UTF_8)
            : "<html><body><h1>Not Found</h1></body></html>"
    );
  };

  @BeforeEach
  void setupResourceResolver() throws Exception {
    ResourceResolver spyResolver = spy(context.resourceResolver());

    Resource folderResourceMock = createResourceMock(JcrResourceConstants.NT_SLING_FOLDER);
    doReturn(folderResourceMock)
        .when(spyResolver)
        .resolve(MAIN_FOLDER_RESOURCE);

    Resource assetResourceMock = createResourceMock("dam:Asset");
    doReturn(assetResourceMock)
        .when(spyResolver)
        .resolve(ArgumentMatchers.<String>argThat(path -> !path.equals(MAIN_FOLDER_RESOURCE)));

    doNothing().when(spyResolver).close();
    doReturn(spyResolver).when(resourceResolverFactoryMock).getAdministrativeResourceResolver(null);
  }

  private static Resource createResourceMock(String primaryNodeType) throws Exception {
    Resource resourceMock = mock(Resource.class);
    Node nodeMock = mock(Node.class);
    NodeType nodeTypeMock = mock(NodeType.class);

    doReturn(primaryNodeType).when(nodeTypeMock).getName();
    doReturn(nodeTypeMock).when(nodeMock).getPrimaryNodeType();
    doReturn(nodeMock).when(resourceMock).adaptTo(Node.class);
    return resourceMock;
  }

  @Test
  void dontHandleUsualAssets() {
    // given
    ResourceContentRelatedResourcesSelector resourceContentRelatedResourcesSelector = new ResourceContentRelatedResourcesSelector(
        new ResourceContentRelatedResourcesSelectorConfig() {

          @Override
          public Class<? extends Annotation> annotationType() {
            return ResourceContentRelatedResourcesSelectorConfig.class;
          }

          @Override
          public String[] references_search$_$regexes() {
            return new String[]{
                "(/content[^\"'\\s]*\\.coreimg\\.[^\"'\\s]*)",
                "(/[^\"'\\s]*etc\\.clientlibs[^\"'\\s]*)"
            };
          }

          @Override
          public String references_exclude$_$from$_$result_regex() {
            return ".*\\{\\.width\\}.*";
          }

          @Override
          public String resource$_$path_postfix$_$to$_$append() {
            return ".html";
          }

          @Override
          public String resource_required$_$path_regex() {
            return "^/content/.*";
          }

          @Override
          public String resource_required$_$primary$_$node$_$type_regex() {
            return JcrResourceConstants.NT_SLING_FOLDER;
          }
        },
        basicRequestProcessor,
        resourceResolverFactoryMock
    );

    // and
    List<ResourceInfo> expectedRelatedResources = Stream.of(
            "/content/firsthops/us/en/_jcr_content/root/container/container/image.coreimg.85.1024.jpeg/1740144613537/mountain-range.jpeg",
            "/content/firsthops/us/en/_jcr_content/root/container/container/image.coreimg.85.1200.jpeg/1740144613537/mountain-range.jpeg",
            "/content/firsthops/us/en/_jcr_content/root/container/container/image.coreimg.85.1600.jpeg/1740144613537/mountain-range.jpeg",
            "/content/firsthops/us/en/_jcr_content/root/container/container/image.coreimg.85.320.jpeg/1740144613537/mountain-range.jpeg",
            "/content/firsthops/us/en/_jcr_content/root/container/container/image.coreimg.85.480.jpeg/1740144613537/mountain-range.jpeg",
            "/content/firsthops/us/en/_jcr_content/root/container/container/image.coreimg.85.600.jpeg/1740144613537/mountain-range.jpeg",
            "/content/firsthops/us/en/_jcr_content/root/container/container/image.coreimg.85.800.jpeg/1740144613537/mountain-range.jpeg",
            "/content/firsthops/us/en/_jcr_content/root/container/container/image.coreimg.jpeg/1740144613537/mountain-range.jpeg",
            "/content/firsthops/us/en/_jcr_content/root/container/container/image_1057652191.coreimg.85.1024.jpeg/1740144616999/lava-rock-formation.jpeg",
            "/content/firsthops/us/en/_jcr_content/root/container/container/image_1057652191.coreimg.85.1200.jpeg/1740144616999/lava-rock-formation.jpeg",
            "/content/firsthops/us/en/_jcr_content/root/container/container/image_1057652191.coreimg.85.1600.jpeg/1740144616999/lava-rock-formation.jpeg",
            "/content/firsthops/us/en/_jcr_content/root/container/container/image_1057652191.coreimg.85.320.jpeg/1740144616999/lava-rock-formation.jpeg",
            "/content/firsthops/us/en/_jcr_content/root/container/container/image_1057652191.coreimg.85.480.jpeg/1740144616999/lava-rock-formation.jpeg",
            "/content/firsthops/us/en/_jcr_content/root/container/container/image_1057652191.coreimg.85.600.jpeg/1740144616999/lava-rock-formation.jpeg",
            "/content/firsthops/us/en/_jcr_content/root/container/container/image_1057652191.coreimg.85.800.jpeg/1740144616999/lava-rock-formation.jpeg",
            "/content/firsthops/us/en/_jcr_content/root/container/container/image_1057652191.coreimg.jpeg/1740144616999/lava-rock-formation.jpeg",
            "/etc.clientlibs/clientlibs/granite/jquery/granite/csrf.lc-56934e461ff6c436f962a5990541a527-lc.min.js",
            "/etc.clientlibs/core/wcm/components/commons/datalayer/acdl/core.wcm.components.commons.datalayer.acdl.lc-bf921af342fd2c40139671dbf0920a1f-lc.min.js",
            "/etc.clientlibs/core/wcm/components/commons/datalayer/v2/clientlibs/core.wcm.components.commons.datalayer.v2.lc-1e0136bad0acfb78be509234578e44f9-lc.min.js",
            "/etc.clientlibs/core/wcm/components/commons/site/clientlibs/container.lc-0a6aff292f5cc42142779cde92054524-lc.min.js",
            "/etc.clientlibs/firsthops/clientlibs/clientlib-base.lc-5b6bf6bddb27a9ef3f911fb1eb20081a-lc.min.css",
            "/etc.clientlibs/firsthops/clientlibs/clientlib-base.lc-86b9d387dd6a9ac638344b5a4522ed15-lc.min.js",
            "/etc.clientlibs/firsthops/clientlibs/clientlib-dependencies.lc-d41d8cd98f00b204e9800998ecf8427e-lc.min.css",
            "/etc.clientlibs/firsthops/clientlibs/clientlib-dependencies.lc-d41d8cd98f00b204e9800998ecf8427e-lc.min.js",
            "/etc.clientlibs/firsthops/clientlibs/clientlib-site.lc-99a5ff922700a9bff656c1db08c6bc22-lc.min.css",
            "/etc.clientlibs/firsthops/clientlibs/clientlib-site.lc-d91e521f6b4cc63fe57186d1b172e7e9-lc.min.js"
        ).map(expectedPath -> new ResourceInfo(expectedPath, "dam:Asset"))
        .collect(Collectors.toUnmodifiableList());

    // when
    Collection<ResourceInfo> actualRelatedResources = resourceContentRelatedResourcesSelector.getRelatedResources(
        MAIN_FOLDER_RESOURCE
    );

    // then
    assertThat(actualRelatedResources)
        .hasSameSizeAs(expectedRelatedResources)
        .containsExactlyElementsOf(expectedRelatedResources);
  }
}
