package dev.streamx.sling.connector.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.streamx.sling.connector.ResourceToIngest;
import dev.streamx.sling.connector.PublicationAction;
import java.util.List;
import java.util.Map;
import org.apache.sling.event.jobs.Job;
import org.junit.jupiter.api.Test;

class IngestionTriggerTest {

  private final Job fakeJob = mock(Job.class);

  @Test
  void mustCreateOutOfJob() {
    // given
    when(fakeJob.getProperty(IngestionTrigger.PN_STREAMX_INGESTION_ACTION, String.class))
        .thenReturn("PUBLISH");
    when(fakeJob.getProperty(IngestionTrigger.PN_STREAMX_RESOURCES_TO_INGEST, String[].class))
        .thenReturn(new String[]{
            new ResourceToIngest("http://localhost:4502/content/we-retail/us/en","cq:Page").serialize(),
            new ResourceToIngest("/content/wknd/us/en", "cq:Page").serialize()
        });

    // when
    IngestionTrigger ingestionTrigger = new IngestionTrigger(fakeJob);

    // then
    assertThat(ingestionTrigger.ingestionAction()).isSameAs(PublicationAction.PUBLISH);
    assertThat(ingestionTrigger.resourcesToIngest()).hasSize(2);
    assertResource(ingestionTrigger.resourcesToIngest().get(0), "http://localhost:4502/content/we-retail/us/en", "cq:Page");
    assertResource(ingestionTrigger.resourcesToIngest().get(1), "/content/wknd/us/en", "cq:Page");
  }

  @Test
  void mustCreateOutOfObjects() {
    // given
    List<ResourceToIngest> resources = List.of(
        new ResourceToIngest("http://localhost:4502/content/we-retail/us/en", "cq:Page"),
        new ResourceToIngest("/content/wknd/us/en", "cq:Page")
    );

    // when
    IngestionTrigger ingestionTrigger = new IngestionTrigger(PublicationAction.PUBLISH, resources);

    // then
    Map<String, Object> jobProps = ingestionTrigger.asJobProps();
    String rawPublicationAction = (String) jobProps.get(IngestionTrigger.PN_STREAMX_INGESTION_ACTION);
    String[] resourcesToIngest = (String[]) jobProps.get(IngestionTrigger.PN_STREAMX_RESOURCES_TO_INGEST);

    assertThat(rawPublicationAction).isEqualTo(PublicationAction.PUBLISH.toString());
    assertThat(resourcesToIngest).hasSize(2);
    assertResource(resourcesToIngest[0], "http://localhost:4502/content/we-retail/us/en", "cq:Page");
    assertResource(resourcesToIngest[1], "/content/wknd/us/en", "cq:Page");
  }

  private static void assertResource(String actualResourceAsJson, String expectedPath, String expectedPrimaryNodeType) {
    ResourceToIngest actualResource = ResourceToIngest.deserialize(actualResourceAsJson);
    assertResource(actualResource, expectedPath, expectedPrimaryNodeType);
  }

  private static void assertResource(ResourceToIngest actualResource, String expectedPath, String expectedPrimaryNodeType) {
    assertThat(actualResource.getPath()).isEqualTo(expectedPath);
    assertThat(actualResource.getPrimaryNodeType()).isEqualTo(expectedPrimaryNodeType);
  }

}
