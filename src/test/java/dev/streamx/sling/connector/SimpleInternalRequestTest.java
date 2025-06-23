package dev.streamx.sling.connector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.streamx.sling.connector.test.util.RandomBytesWriter;
import dev.streamx.sling.connector.util.SimpleInternalRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.api.uri.SlingUriBuilder;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlingContextExtension.class)
class SimpleInternalRequestTest {

  private static final String PLAIN_MARS_URL = "/content/mars-page.plain.html";
  private static final String PLAIN_MARS_HTML = "<html><body><h1>Plain Mars Page</h1></body></html>";

  private static final String USUAL_MARS_URL = "/content/usual-mars-page.html";
  private static final String USUAL_MARS_HTML = "<html><body><h1>Usual Mars Page</h1></body></html>";

  private static final String NOT_FOUND_URL = "/content/unknown-page.html";
  private static final String NOT_FOUND_HTML = "<html><body><h1>Not Found</h1></body></html>";

  private static final String BINARY_URL = "/content/firsthops/us/en/_jcr_content/root/container/container/image.coreimg.85.800.jpeg/1741014797808/lava-rock-formation.jpeg";
  private static final int BINARY_PAGE_LENGTH = 1024;

  private static final String ADDITIONAL_PROPERTY_NAME = "foo";
  private static final String ADDITIONAL_PROPERTY_VALUE = "bar";

  private final SlingContext context = new SlingContext();
  private final ResourceResolver resourceResolver = context.resourceResolver();

  private final SlingRequestProcessor slingRequestProcessor = (HttpServletRequest request, HttpServletResponse response, ResourceResolver resolver) -> {
    switch (request.getRequestURI()) {
      case PLAIN_MARS_URL:
        response.setContentType("text/html");
        response.getWriter().write(PLAIN_MARS_HTML);
        break;
      case USUAL_MARS_URL:
        response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(USUAL_MARS_HTML);
        break;
      case BINARY_URL:
        RandomBytesWriter.writeRandomBytes(response, BINARY_PAGE_LENGTH);
        break;
      default:
        response.setContentType("text/html");
        response.getWriter().write(NOT_FOUND_HTML);
        break;
    }

    if (ADDITIONAL_PROPERTY_VALUE.equals(request.getParameter(ADDITIONAL_PROPERTY_NAME))) {
      response.getWriter().write("Received " + ADDITIONAL_PROPERTY_NAME + " = " + ADDITIONAL_PROPERTY_VALUE + " flag");
    }
  };

  @Test
  void mustProduceStringForPlainMarsPage() throws IOException {
    // given
    SlingUri plainMarsUri = SlingUriBuilder.parse(PLAIN_MARS_URL, resourceResolver).build();

    // when
    SimpleInternalRequest plainMarsRequest = new SimpleInternalRequest(plainMarsUri, slingRequestProcessor, resourceResolver);

    // then
    assertThat(plainMarsRequest.getResponseAsString()).isEqualTo(PLAIN_MARS_HTML);
    assertThat(bytesOf(plainMarsRequest.getResponseAsInputStream())).hasSize(PLAIN_MARS_HTML.length());
    assertThat(plainMarsRequest.getResponseAsBytes()).hasValue(PLAIN_MARS_HTML.getBytes(UTF_8));
  }

  @Test
  void mustProduceStringForUsualMarsPage() throws IOException {
    // given
    SlingUri usualMarsUri = SlingUriBuilder.parse(USUAL_MARS_URL, resourceResolver).build();

    // when
    SimpleInternalRequest usualMarsRequest = new SimpleInternalRequest(usualMarsUri, slingRequestProcessor, resourceResolver);

    // then
    assertThat(usualMarsRequest.getResponseAsString()).isEqualTo(USUAL_MARS_HTML);
    assertThat(bytesOf(usualMarsRequest.getResponseAsInputStream())).hasSize(USUAL_MARS_HTML.length());
    assertThat(usualMarsRequest.getResponseAsBytes()).contains(USUAL_MARS_HTML.getBytes(UTF_8));
  }

  @Test
  void mustProduceStringForUnknownPage() throws IOException {
    // given
    SlingUri unknownPageUri = SlingUriBuilder.parse(NOT_FOUND_URL, resourceResolver).build();

    // when
    SimpleInternalRequest unknownRequest = new SimpleInternalRequest(unknownPageUri, slingRequestProcessor, resourceResolver);

    // then
    assertThat(unknownRequest.getResponseAsString()).isEqualTo(NOT_FOUND_HTML);
    assertThat(bytesOf(unknownRequest.getResponseAsInputStream())).hasSize(NOT_FOUND_HTML.length());
    assertThat(unknownRequest.getResponseAsBytes()).contains(NOT_FOUND_HTML.getBytes(UTF_8));
  }

  @Test
  void mustProduceBinaryForBinaryPage() throws IOException {
    // given
    SlingUri binaryUri = SlingUriBuilder.parse(BINARY_URL, resourceResolver).build();

    // when
    SimpleInternalRequest binaryRequest = new SimpleInternalRequest(binaryUri, slingRequestProcessor, resourceResolver);

    // then
    assertThat(bytesOf(binaryRequest.getResponseAsInputStream())).hasSize(BINARY_PAGE_LENGTH);
    assertThat(binaryRequest.getResponseAsBytes()).isPresent();
    assertThat(binaryRequest.getResponseAsBytes().get()).hasSize(BINARY_PAGE_LENGTH);
  }

  @Test
  void shouldPassAdditionalParametersToTheRequest() {
    // given
    SlingUri uri = SlingUriBuilder.parse("foo-bar.html", resourceResolver).build();
    Map<String, String> additionalProperties = Map.of(ADDITIONAL_PROPERTY_NAME, ADDITIONAL_PROPERTY_VALUE);

    // when
    SimpleInternalRequest request = new SimpleInternalRequest(uri, slingRequestProcessor, resourceResolver, additionalProperties);

    // then
    assertThat(request.getResponseAsString()).contains("Received foo = bar flag");
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private static byte[] bytesOf(Optional<InputStream> optionalInputStream) throws IOException {
    try (InputStream inputStream = optionalInputStream.orElseThrow()) {
      return inputStream.readAllBytes();
    }
  }
}
