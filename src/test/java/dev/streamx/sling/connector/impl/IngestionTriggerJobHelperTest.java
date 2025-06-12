package dev.streamx.sling.connector.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.test.util.PageResourceInfo;
import java.util.List;
import java.util.Map;
import org.apache.sling.event.jobs.Job;
import org.junit.jupiter.api.Test;

class IngestionTriggerJobHelperTest {

  private final Job fakeJob = mock(Job.class);

  @Test
  void mustExtractDataOutOfJob() {
    // given
    when(fakeJob.getProperty(IngestionTriggerJobHelper.PN_STREAMX_INGESTION_ACTION, String.class))
        .thenReturn("PUBLISH");
    when(fakeJob.getProperty(IngestionTriggerJobHelper.PN_STREAMX_RESOURCES_INFO, String[].class))
        .thenReturn(new String[]{
            new PageResourceInfo("http://localhost:4502/content/we-retail/us/en").serialize(),
            new PageResourceInfo("/content/wknd/us/en").serialize()
        });

    // when
    PublicationAction publicationAction = IngestionTriggerJobHelper.extractPublicationAction(fakeJob);
    List<ResourceInfo> resourcesInfo = IngestionTriggerJobHelper.extractResourcesInfo(fakeJob);

    // then
    assertThat(publicationAction).isSameAs(PublicationAction.PUBLISH);
    assertThat(resourcesInfo).hasSize(2);
    assertResource(resourcesInfo.get(0), "http://localhost:4502/content/we-retail/us/en", "cq:Page");
    assertResource(resourcesInfo.get(1), "/content/wknd/us/en", "cq:Page");
  }

  @Test
  void mustExtractJobPropertiesOutOfData() {
    // given
    List<ResourceInfo> resources = List.of(
        new PageResourceInfo("http://localhost:4502/content/we-retail/us/en"),
        new PageResourceInfo("/content/wknd/us/en")
    );

    // when
    Map<String, Object> jobProps = IngestionTriggerJobHelper.asJobProps(PublicationAction.PUBLISH, resources);

    // then
    String rawPublicationAction = (String) jobProps.get(IngestionTriggerJobHelper.PN_STREAMX_INGESTION_ACTION);
    String[] resourcesInfo = (String[]) jobProps.get(IngestionTriggerJobHelper.PN_STREAMX_RESOURCES_INFO);

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
