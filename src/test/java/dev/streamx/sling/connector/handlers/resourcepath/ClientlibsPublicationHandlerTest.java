package dev.streamx.sling.connector.handlers.resourcepath;

import static org.assertj.core.api.Assertions.assertThat;

import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.UnpublishData;
import dev.streamx.sling.connector.test.util.RandomBytesWriter;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlingContextExtension.class)
class ClientlibsPublicationHandlerTest {

  private static final Map<String, Integer> webResourcePathsAndSizes = Map.of(
      "/etc.clientlibs/clientlibs/granite/jquery/granite/csrf.lc-56934e461ff6c436f962a5990541a527-lc.min.js",
      1,
      "/etc.clientlibs/core/wcm/components/commons/datalayer/acdl/core.wcm.components.commons.datalayer.acdl.lc-bf921af342fd2c40139671dbf0920a1f-lc.min.js",
      2,
      "/etc.clientlibs/core/wcm/components/commons/datalayer/v2/clientlibs/core.wcm.components.commons.datalayer.v2.lc-1e0136bad0acfb78be509234578e44f9-lc.min.js",
      3,
      "/etc.clientlibs/core/wcm/components/commons/site/clientlibs/container.lc-0a6aff292f5cc42142779cde92054524-lc.min.js",
      4,
      "/etc.clientlibs/firsthops/clientlibs/clientlib-base.lc-5b6bf6bddb27a9ef3f911fb1eb20081a-lc.min.css",
      5,
      "/etc.clientlibs/firsthops/clientlibs/clientlib-base.lc-86b9d387dd6a9ac638344b5a4522ed15-lc.min.js",
      6,
      "/etc.clientlibs/firsthops/clientlibs/clientlib-dependencies.lc-d41d8cd98f00b204e9800998ecf8427e-lc.min.css",
      7,
      "/etc.clientlibs/firsthops/clientlibs/clientlib-dependencies.lc-d41d8cd98f00b204e9800998ecf8427e-lc.min.js",
      8,
      "/etc.clientlibs/firsthops/clientlibs/clientlib-site.lc-99a5ff922700a9bff656c1db08c6bc22-lc.min.css",
      9,
      "/etc.clientlibs/firsthops/clientlibs/clientlib-site.lc-d91e521f6b4cc63fe57186d1b172e7e9-lc.min.js",
      10
  );

  private final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

  private final SlingRequestProcessor basicRequestProcessor = (HttpServletRequest request, HttpServletResponse response, ResourceResolver resolver) -> {
    String requestURI = request.getRequestURI();
    assertThat(webResourcePathsAndSizes).containsKey(requestURI);
    Integer size = webResourcePathsAndSizes.get(requestURI);
    RandomBytesWriter.writeRandomBytes(response, size);
  };

  @Test
  void canGetPublishData() throws StreamxPublicationException {
    PublicationHandler<WebResource> handler = new ClientlibsPublicationHandler(
        context.getService(ResourceResolverFactory.class),
        basicRequestProcessor,
        new ClientlibsPublicationHandlerConfig() {
          @Override
          public Class<? extends Annotation> annotationType() {
            return ClientlibsPublicationHandlerConfig.class;
          }

          @Override
          public String clientlibs_path_regexp() {
            return ".*";
          }

          @Override
          public String publication_channel() {
            return "web-resources";
          }

          @Override
          public boolean enabled() {
            return true;
          }
        }
    );
    for (Entry<String, Integer> entry : webResourcePathsAndSizes.entrySet()) {
      String webResourcePath = entry.getKey();
      Integer expectedSize = entry.getValue();
      ResourceInfo resource = new ResourceInfo(webResourcePath, "dam:Asset");
      assertThat(handler.canHandle(resource)).isTrue();

      PublishData<WebResource> publishData = handler.getPublishData(webResourcePath);
      assertThat(publishData.getModel()).isInstanceOf(WebResource.class);
      assertThat(publishData.getModel().getContent().array()).hasSize(expectedSize);
      assertThat(publishData.getKey()).isEqualTo(webResourcePath);
      assertThat(publishData.getChannel()).isEqualTo("web-resources");

      UnpublishData<WebResource> unpublishData = handler.getUnpublishData(webResourcePath);
      assertThat(unpublishData.getKey()).isEqualTo(webResourcePath);
      assertThat(unpublishData.getChannel()).isEqualTo("web-resources");
    }
  }

}
