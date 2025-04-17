package dev.streamx.sling.connector.impl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import dev.streamx.sling.connector.ResourceData;
import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.util.DefaultSlingUriBuilder;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({SlingContextExtension.class, MockitoExtension.class})
class JobAsResourceDataTest {

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
    ResourceData jobAsResourceData = new JobAsResourceData(fakeJob, resourceResolverFactory);
    assertAll(
        () -> Assertions.assertEquals(PublicationAction.PUBLISH,
            jobAsResourceData.ingestionAction()),
        () -> assertEquals(
            "http://localhost:4502/content/we-retail/us/en",
            jobAsResourceData.uriToIngest().toString()
        )
    );
  }

  @Test
  void mustCreateOutOfObjects() {
    SlingUri slingUri = new DefaultSlingUriBuilder(
        "http://localhost:4502/content/we-retail/us/en", resourceResolverFactory
    ).build();
    Map<String, Object> jobProps = toJobProps(slingUri);
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

  private static Map<String, Object> toJobProps(SlingUri slingUri) {
    JobAsResourceData jobAsIngestedData = new JobAsResourceData(
        new ResourceData() {
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
    return jobAsIngestedData.asJobProps();
  }

}
