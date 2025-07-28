package dev.streamx.sling.connector.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.test.util.PageResourceInfo;
import java.util.List;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.event.jobs.Job;
import org.junit.jupiter.api.Test;

class IngestionTriggerJobExecutorTest {

  private final Job fakeJob = mock(Job.class);

  @Test
  void mustExtractDataOutOfJob() {
    // given
    when(fakeJob.getProperty(IngestionTriggerJobExecutor.PN_STREAMX_INGESTION_ACTION, String.class))
        .thenReturn("PUBLISH");
    when(fakeJob.getProperty(IngestionTriggerJobExecutor.PN_STREAMX_INGESTION_RESOURCES, String[].class))
        .thenReturn(new String[]{
            new PageResourceInfo("http://localhost:4502/content/we-retail/us/en").serialize(),
            new PageResourceInfo("/content/wknd/us/en").serialize()
        });

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
