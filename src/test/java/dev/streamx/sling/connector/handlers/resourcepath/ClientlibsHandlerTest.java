package dev.streamx.sling.connector.handlers.resourcepath;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.StreamxPublicationException;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import javax.servlet.ServletResponse;
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
class ClientlibsHandlerTest {

  private final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

  private static class BasicRequestProcessor implements SlingRequestProcessor {

    @SuppressWarnings({"IfCanBeSwitch", "IfStatementWithTooManyBranches", "OverlyComplexMethod"})
    @Override
    public void processRequest(
        HttpServletRequest request, HttpServletResponse response, ResourceResolver resourceResolver
    ) throws IOException {
      String requestURI = request.getRequestURI();
      if (requestURI.equals(
          "/etc.clientlibs/clientlibs/granite/jquery/granite/csrf.lc-56934e461ff6c436f962a5990541a527-lc.min.js")) {
        writeInto(response, 1);
      } else if (requestURI.equals(
          "/etc.clientlibs/core/wcm/components/commons/datalayer/acdl/core.wcm.components.commons.datalayer.acdl.lc-bf921af342fd2c40139671dbf0920a1f-lc.min.js")) {
        writeInto(response, 2);
      } else if (requestURI.equals(
          "/etc.clientlibs/core/wcm/components/commons/datalayer/v2/clientlibs/core.wcm.components.commons.datalayer.v2.lc-1e0136bad0acfb78be509234578e44f9-lc.min.js")) {
        writeInto(response, 3);
      } else if (requestURI.equals(
          "/etc.clientlibs/core/wcm/components/commons/site/clientlibs/container.lc-0a6aff292f5cc42142779cde92054524-lc.min.js")) {
        writeInto(response, 4);
      } else if (requestURI.equals(
          "/etc.clientlibs/firsthops/clientlibs/clientlib-base.lc-5b6bf6bddb27a9ef3f911fb1eb20081a-lc.min.css")) {
        writeInto(response, 5);
      } else if (requestURI.equals(
          "/etc.clientlibs/firsthops/clientlibs/clientlib-base.lc-86b9d387dd6a9ac638344b5a4522ed15-lc.min.js")) {
        writeInto(response, 6);
      } else if (requestURI.equals(
          "/etc.clientlibs/firsthops/clientlibs/clientlib-dependencies.lc-d41d8cd98f00b204e9800998ecf8427e-lc.min.css")) {
        writeInto(response, 7);
      } else if (requestURI.equals(
          "/etc.clientlibs/firsthops/clientlibs/clientlib-dependencies.lc-d41d8cd98f00b204e9800998ecf8427e-lc.min.js")) {
        writeInto(response, 8);
      } else if (requestURI.equals(
          "/etc.clientlibs/firsthops/clientlibs/clientlib-site.lc-99a5ff922700a9bff656c1db08c6bc22-lc.min.css")) {
        writeInto(response, 9);
      } else if (requestURI.equals(
          "/etc.clientlibs/firsthops/clientlibs/clientlib-site.lc-d91e521f6b4cc63fe57186d1b172e7e9-lc.min.js")) {
        writeInto(response, 10);
      } else {
        response.setContentType("text/html");
        response.getWriter().write("<html><body><h1>Not Found</h1></body></html>");
      }
    }

    private void writeInto(ServletResponse response, int dataSize) throws IOException {
      response.setContentType("application/octet-stream");
      response.setContentLength(dataSize);
      byte[] randomData = new byte[dataSize];
      new Random().nextBytes(randomData);
      try (OutputStream out = response.getOutputStream()) {
        out.write(randomData);
        out.flush();
      }
    }
  }

  @Test
  void canGetPublishData() {
    Map<String, Integer> webResourcePaths = Map.of(
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
    PublicationHandler<WebResource> handler = new ClientlibsHandler(
        Objects.requireNonNull(context.getService(ResourceResolverFactory.class)),
        new BasicRequestProcessor(),
        new ClientlibsHandlerConfig() {
          @Override
          public Class<? extends Annotation> annotationType() {
            return ClientlibsHandlerConfig.class;
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
    webResourcePaths.forEach(
        (webResourcePath, expectedSize) -> {
          PublishData<WebResource> publishData;
          try {
            publishData = handler.getPublishData(webResourcePath);
          } catch (StreamxPublicationException e) {
            throw new RuntimeException(e);
          }
          int length = publishData.getModel().getContent().array().length;
          String key = publishData.getKey();
          WebResource model = publishData.getModel();
          String channel = publishData.getChannel();
          assertAll(
              () -> assertEquals(expectedSize, length),
              () -> assertEquals(key, webResourcePath),
              () -> assertEquals("web-resources", channel),
              () -> assertEquals(WebResource.class, model.getClass())
          );
        }
    );
  }

}
