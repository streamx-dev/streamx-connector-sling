package dev.streamx.sling.connector.impl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.test.util.PageResourceInfo;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJob;
import java.util.List;
import org.apache.jackrabbit.JcrConstants;
import org.junit.jupiter.api.Test;

class IngestionTriggerJobExecutorTest {

  @Test
  void mustExtractDataOutOfJob() {
    // given
    FakeJob fakeJob = new FakeJob(
        "topic",
        new IngestionTriggerJobProperties()
            .withAction(PublicationAction.PUBLISH)
            .withResources(List.of(
                new PageResourceInfo("http://localhost:4502/content/we-retail/us/en"),
                new PageResourceInfo("/content/wknd/us/en")
            )).asMap()
    );

    // when
    PublicationAction publicationAction = IngestionTriggerJobExecutor.extractPublicationAction(fakeJob);
    List<ResourceInfo> resourcesInfo = IngestionTriggerJobExecutor.extractResourcesInfo(fakeJob);

    // then
    assertThat(publicationAction).isSameAs(PublicationAction.PUBLISH);
    assertThat(resourcesInfo).hasSize(2);
    assertResource(resourcesInfo.get(0), "http://localhost:4502/content/we-retail/us/en", "cq:Page");
    assertResource(resourcesInfo.get(1), "/content/wknd/us/en", "cq:Page");
  }

  private static void assertResource(ResourceInfo actualResource, String expectedPath, String expectedPrimaryNodeType) {
    assertThat(actualResource.getPath()).isEqualTo(expectedPath);
    assertThat(actualResource.getProperties()).containsEntry(JcrConstants.JCR_PRIMARYTYPE, expectedPrimaryNodeType);
  }

}
