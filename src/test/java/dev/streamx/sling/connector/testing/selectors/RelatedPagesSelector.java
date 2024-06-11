package dev.streamx.sling.connector.testing.selectors;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.RelatedResource;
import dev.streamx.sling.connector.RelatedResourcesSelector;
import java.util.Arrays;
import java.util.Collection;

public class RelatedPagesSelector implements RelatedResourcesSelector {

  @Override
  public Collection<RelatedResource> getRelatedResources(String resourcePath,
      PublicationAction action) {
    return Arrays.asList(
        new RelatedResource("/content/my-site/related-page-to-publish",
            PublicationAction.PUBLISH),
        new RelatedResource("/content/my-site/related-page-to-unpublish",
            PublicationAction.UNPUBLISH)
    );
  }
}
