package dev.streamx.sling.connector.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.streamx.sling.connector.ResourceInfo;
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
    when(fakeJob.getProperty(IngestionTrigger.PN_STREAMX_RESOURCES_INFO, String[].class))
        .thenReturn(new String[]{
            new ResourceInfo("http://localhost:4502/content/we-retail/us/en","cq:Page").serialize(),
            new ResourceInfo("/content/wknd/us/en", "cq:Page").serialize()
        });

    // when
    IngestionTrigger ingestionTrigger = new IngestionTrigger(fakeJob);

    // then
    assertThat(ingestionTrigger.ingestionAction()).isSameAs(PublicationAction.PUBLISH);
    assertThat(ingestionTrigger.resourcesInfo()).hasSize(2);
    assertResource(ingestionTrigger.resourcesInfo().get(0), "http://localhost:4502/content/we-retail/us/en", "cq:Page");
    assertResource(ingestionTrigger.resourcesInfo().get(1), "/content/wknd/us/en", "cq:Page");
  }

  @Test
  void mustCreateOutOfObjects() {
    // given
    List<ResourceInfo> resources = List.of(
        new ResourceInfo("http://localhost:4502/content/we-retail/us/en", "cq:Page"),
        new ResourceInfo("/content/wknd/us/en", "cq:Page")
    );

    // when
    IngestionTrigger ingestionTrigger = new IngestionTrigger(PublicationAction.PUBLISH, resources);

    // then
    Map<String, Object> jobProps = ingestionTrigger.asJobProps();
    String rawPublicationAction = (String) jobProps.get(IngestionTrigger.PN_STREAMX_INGESTION_ACTION);
    String[] resourcesInfo = (String[]) jobProps.get(IngestionTrigger.PN_STREAMX_RESOURCES_INFO);

    assertThat(rawPublicationAction).isEqualTo(PublicationAction.PUBLISH.toString());
    assertThat(resourcesInfo).hasSize(2);
    assertResource(resourcesInfo[0], "http://localhost:4502/content/we-retail/us/en", "cq:Page");
    assertResource(resourcesInfo[1], "/content/wknd/us/en", "cq:Page");
  }

  private static void assertResource(String actualResourceAsJson, String expectedPath, String expectedPrimaryNodeType) {
    ResourceInfo actualResource = ResourceInfo.deserialize(actualResourceAsJson);
    assertResource(actualResource, expectedPath, expectedPrimaryNodeType);
  }

  private static void assertResource(ResourceInfo actualResource, String expectedPath, String expectedPrimaryNodeType) {
    assertThat(actualResource.getPath()).isEqualTo(expectedPath);
    assertThat(actualResource.getPrimaryNodeType()).isEqualTo(expectedPrimaryNodeType);
  }

}
