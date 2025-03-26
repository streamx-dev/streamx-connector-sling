package dev.streamx.sling.connector.impl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import dev.streamx.sling.connector.IngestedData;
import dev.streamx.sling.connector.PublicationAction;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
class JobAsIngestedDataTest {

  private static final Logger LOG = LoggerFactory.getLogger(JobAsIngestedDataTest.class);
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
    when(fakeJob.getProperty("streamx.uriToIngest", String.class))
        .thenReturn("http://localhost:4502/content/we-retail/us/en");
    JobAsIngestedData jobAsIngestedData = new JobAsIngestedData(fakeJob, resourceResolverFactory);
    assertAll(
        () -> Assertions.assertEquals(PublicationAction.PUBLISH,
            jobAsIngestedData.ingestionAction()),
        () -> assertEquals(
            "http://localhost:4502/content/we-retail/us/en",
            jobAsIngestedData.uriToIngest().toString()
        )
    );
  }

  @Test
  void mustCreateOutOfObjects() {
    SlingUri slingUri = toSlingUri(
        "http://localhost:4502/content/we-retail/us/en", resourceResolverFactory
    ).orElseThrow();
    JobAsIngestedData jobAsIngestedData = new JobAsIngestedData(
        new IngestedData() {
          @Override
          public PublicationAction ingestionAction() {
            return PublicationAction.PUBLISH;
          }

          @Override
          public SlingUri uriToIngest() {
            return slingUri;
          }

          @Override
          public Map<String, Object> properties() {
            return Map.of();
          }
        }
    );
    Map<String, Object> jobProps = jobAsIngestedData.asJobProps();
    String rawPublicationAction = Optional.ofNullable(jobProps.get("streamx.ingestionAction"))
        .map(String.class::cast)
        .orElseThrow();
    String uriToIngest = Optional.ofNullable(jobProps.get("streamx.uriToIngest"))
        .map(String.class::cast)
        .orElseThrow();
    assertAll(
        () -> assertEquals(PublicationAction.PUBLISH.toString(), rawPublicationAction),
        () -> assertEquals(
            "http://localhost:4502/content/we-retail/us/en",
            uriToIngest
        )
    );
  }

  @SuppressWarnings({"deprecation", "SameParameterValue"})
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
