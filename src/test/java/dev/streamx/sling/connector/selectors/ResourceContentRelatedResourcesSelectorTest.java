package dev.streamx.sling.connector.selectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.RelatedResource;
import dev.streamx.sling.connector.selectors.content.ResourceContentRelatedResourcesSelector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlingContextExtension.class)
class ResourceContentRelatedResourcesSelectorTest {

  private final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

  private static class BasicRequestProcessor implements SlingRequestProcessor {

    @Override
    public void processRequest(
        HttpServletRequest request, HttpServletResponse response, ResourceResolver resourceResolver
    ) throws IOException {
      String requestURI = request.getRequestURI();
      if (requestURI.equals("/content/firsthops/us/en.html")) {
        String sampleHtml = IOUtils.resourceToString(
            "sample-page.html", StandardCharsets.UTF_8, ResourceContentRelatedResourcesSelectorTest.class.getClassLoader()
        );
        response.setContentType("text/html");
        response.getWriter().write(sampleHtml);
      } else {
        response.setContentType("text/html");
        response.getWriter().write("<html><body><h1>Not Found</h1></body></html>");
      }
    }
  }

  @BeforeEach
  void setup() {
    context.registerService(SlingRequestProcessor.class, new BasicRequestProcessor());
    context.build().resource(
        "/content/firsthops/us/en", Map.of(
            JcrConstants.JCR_PRIMARYTYPE, JcrResourceConstants.NT_SLING_FOLDER
        )
    ).commit();
  }

  @Test
  void dontHandleUsualAssets() {
    String path = "/content/firsthops/us/en";
    ResourceContentRelatedResourcesSelector resourceContentRelatedResourcesSelector = context.registerInjectActivateService(
        ResourceContentRelatedResourcesSelector.class,
        Map.of(
            "references.search-regexes", new String[]{
                "(/[^\"'\\s]*\\.coreimg\\.[^\"'\\s]*)",
                "(/[^\"'\\s]*etc\\.clientlibs[^\"'\\s]*)"
            },
            "references.exclude-from-result.regex", ".*\\{\\.width\\}.*",
            "resource-path.postfix-to-append", ".html",
            "resource.required-path.regex", "^/content/.*",
            "resource.required-primary-node-type.regex", JcrResourceConstants.NT_SLING_FOLDER
        )
    );
    List<RelatedResource> expectedResources = Stream.of(
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
        ).map(expectedPath -> new RelatedResource(expectedPath, PublicationAction.PUBLISH))
        .collect(Collectors.toUnmodifiableList());
    Collection<RelatedResource> actualResources = resourceContentRelatedResourcesSelector.getRelatedResources(
        path, PublicationAction.PUBLISH
    );
    assertAll(
        () -> assertTrue(actualResources.containsAll(expectedResources)),
        () -> assertEquals(expectedResources.size(), actualResources.size())
    );
  }
}
