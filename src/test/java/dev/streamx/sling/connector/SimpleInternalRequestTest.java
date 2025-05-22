package dev.streamx.sling.connector;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.streamx.sling.connector.util.SimpleInternalRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.api.uri.SlingUriBuilder;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlingContextExtension.class)
class SimpleInternalRequestTest {

  private SlingContext context;
  private SlingRequestProcessor slingRequestProcessor;

  private static class BasicRequestProcessor implements SlingRequestProcessor {

    @SuppressWarnings({"IfCanBeSwitch", "IfStatementWithTooManyBranches"})
    @Override
    public void processRequest(
        HttpServletRequest request, HttpServletResponse response, ResourceResolver resourceResolver
    ) throws IOException {
      String requestURI = request.getRequestURI();
      if (requestURI.equals("/content/mars-page.plain.html")) {
        response.setContentType("text/html");
        response.getWriter().write("<html><body><h1>Plain Mars Page</h1></body></html>");
      } else if (requestURI.equals("/content/usual-mars-page.html")) {
        response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write("<html><body><h1>Usual Mars Page</h1></body></html>");
      } else if (requestURI.equals("/content/firsthops/us/en/_jcr_content/root/container/container/image.coreimg.85.800.jpeg/1741014797808/lava-rock-formation.jpeg")) {
        @SuppressWarnings({"MagicNumber", "squid:S117"})
        int dataSize = 1024;
        response.setContentType("application/octet-stream");
        response.setContentLength(dataSize);
        byte[] randomData = new byte[dataSize];
        new Random().nextBytes(randomData);
        try (OutputStream out = response.getOutputStream()) {
          out.write(randomData);
          out.flush();
        }
      } else {
        response.setContentType("text/html");
        response.getWriter().write("<html><body><h1>Not Found</h1></body></html>");
      }
    }
  }

  @BeforeEach
  void setup() {
    context = new SlingContext();
    slingRequestProcessor = context.registerService(
        SlingRequestProcessor.class,
        new BasicRequestProcessor()
    );
  }

  @SuppressWarnings({"MagicNumber", "resource"})
  @Test
  void mustProduceString() {
    ResourceResolver resourceResolver = context.resourceResolver();
    SlingUri plainMarsUri = SlingUriBuilder.parse(
        "/content/mars-page.plain.html", resourceResolver
    ).build();
    SlingUri usualMarsUri = SlingUriBuilder.parse(
        "/content/usual-mars-page.html", resourceResolver
    ).build();
    SlingUri unknownPageUri = SlingUriBuilder.parse(
        "/content/unknown-page.html", resourceResolver
    ).build();
    SlingUri binaryUri = SlingUriBuilder.parse(
        "/content/firsthops/us/en/_jcr_content/root/container/container/image.coreimg.85.800.jpeg/1741014797808/lava-rock-formation.jpeg", resourceResolver
    ).build();
    SimpleInternalRequest plainMarsRequest = new SimpleInternalRequest(
        plainMarsUri, slingRequestProcessor, resourceResolver
    );
    SimpleInternalRequest usualMarsRequest = new SimpleInternalRequest(
        usualMarsUri, slingRequestProcessor, resourceResolver
    );
    SimpleInternalRequest unknownRequest = new SimpleInternalRequest(
        unknownPageUri, slingRequestProcessor, resourceResolver
    );
    SimpleInternalRequest binaryRequest = new SimpleInternalRequest(
        binaryUri, slingRequestProcessor, resourceResolver
    );

    assertAll(
        () -> assertEquals(
            "<html><body><h1>Plain Mars Page</h1></body></html>",
            plainMarsRequest.getResponseAsString()
        ),
        () -> assertEquals(
            "<html><body><h1>Usual Mars Page</h1></body></html>",
            usualMarsRequest.getResponseAsString()
        ),
        () -> assertEquals(
            "<html><body><h1>Not Found</h1></body></html>",
            unknownRequest.getResponseAsString()
        ),
        () -> assertEquals(
            50, plainMarsRequest.getResponseAsInputStream().orElseThrow().readAllBytes().length
        ),
        () -> assertEquals(
            1024, binaryRequest.getResponseAsInputStream().orElseThrow().readAllBytes().length
        )
    );
  }

}
