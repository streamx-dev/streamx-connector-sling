package dev.streamx.sling.connector.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import dev.streamx.sling.connector.PublicationAction;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.api.uri.SlingUriBuilder;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith({SlingContextExtension.class, MockitoExtension.class})
class IngestionTriggerTest {

  private static final Logger LOG = LoggerFactory.getLogger(IngestionTriggerTest.class);
  private final SlingContext context = new SlingContext();
  private ResourceResolverFactory resourceResolverFactory;

  @Mock
  private Job fakeJob;

  @BeforeEach
  void setup() {
    resourceResolverFactory = Objects.requireNonNull(
        context.getService(ResourceResolverFactory.class)
    );
  }

  @Test
  void mustCreateOutOfJob() {
    when(fakeJob.getProperty("streamx.ingestionAction", String.class))
        .thenReturn("PUBLISH");
    when(fakeJob.getProperty("streamx.urisToIngest", String[].class))
        .thenReturn(new String[]{
            "http://localhost:4502/content/we-retail/us/en",
            "/content/wknd/us/en"
        });
    IngestionTrigger ingestionTrigger = new IngestionTrigger(fakeJob, resourceResolverFactory);
    assertAll(
        () -> Assertions.assertEquals(PublicationAction.PUBLISH, ingestionTrigger.ingestionAction()),
        () -> assertEquals(2, ingestionTrigger.urisToIngest().size()),
        () -> assertEquals(
            "http://localhost:4502/content/we-retail/us/en",
            ingestionTrigger.urisToIngest().get(NumberUtils.INTEGER_ZERO).toString()
        ),
        () -> assertEquals(
            "/content/wknd/us/en",
            ingestionTrigger.urisToIngest().get(NumberUtils.INTEGER_ONE).toString()
        )
    );
  }

  @Test
  void mustCreateOutOfObjects() {
    List<SlingUri> slingUris = Stream.of(
        "http://localhost:4502/content/we-retail/us/en",
            "/content/wknd/us/en"
        ).map(uri -> toSlingUri(uri, resourceResolverFactory))
        .flatMap(Optional::stream)
        .collect(Collectors.toUnmodifiableList());
    IngestionTrigger ingestionTrigger = new IngestionTrigger(PublicationAction.PUBLISH, slingUris);
    Map<String, Object> jobProps = ingestionTrigger.asJobProps();
    String rawPublicationAction = Optional.ofNullable(jobProps.get("streamx.ingestionAction"))
        .map(String.class::cast)
        .orElseThrow();
    String[] urisToIngest  = Optional.ofNullable(jobProps.get("streamx.urisToIngest"))
        .map(String[].class::cast)
        .orElseThrow();
    assertAll(
        () -> assertEquals(PublicationAction.PUBLISH.toString(), rawPublicationAction),
        () -> assertEquals(2, urisToIngest.length),
        () -> assertEquals(
            "http://localhost:4502/content/we-retail/us/en",
            urisToIngest[NumberUtils.INTEGER_ZERO]
        ),
        () -> assertEquals(
            "/content/wknd/us/en",
            urisToIngest[NumberUtils.INTEGER_ONE]
        )
    );
  }

  @SuppressWarnings("deprecation")
  private Optional<SlingUri> toSlingUri(
      String rawUri, ResourceResolverFactory resourceResolverFactory
  ) {
    try (
        ResourceResolver resourceResolver
            = resourceResolverFactory.getAdministrativeResourceResolver(null)
    ) {
      SlingUri slingUri = SlingUriBuilder.parse(rawUri, resourceResolver).build();
      return Optional.of(slingUri);
    } catch (LoginException exception) {
      String message = String.format("Unable to parse URI: '%s'", rawUri);
      LOG.error(message, exception);
      return Optional.empty();
    }
  }

}
