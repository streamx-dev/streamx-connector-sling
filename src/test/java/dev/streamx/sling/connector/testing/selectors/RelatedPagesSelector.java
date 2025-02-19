package dev.streamx.sling.connector.testing.selectors;

import dev.streamx.sling.connector.IngestionActionType;
import dev.streamx.sling.connector.RelatedResource;
import dev.streamx.sling.connector.RelatedResourcesSelector;
import java.util.Arrays;
import java.util.Collection;

public class RelatedPagesSelector implements RelatedResourcesSelector {

  @Override
  public Collection<RelatedResource> getRelatedResources(String resourcePath,
      IngestionActionType ingestionActionType) {
    return Arrays.asList(
        new RelatedResource("/content/my-site/related-page-to-publish",
            IngestionActionType.PUBLISH),
        new RelatedResource("/content/my-site/related-page-to-unpublish",
            IngestionActionType.UNPUBLISH)
    );
  }
}
