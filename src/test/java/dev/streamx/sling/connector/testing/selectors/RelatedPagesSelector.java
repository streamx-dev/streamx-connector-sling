package dev.streamx.sling.connector.testing.selectors;

import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.ResourceInfo;
import java.util.Arrays;
import java.util.Collection;

public class RelatedPagesSelector implements RelatedResourcesSelector {

  @Override
  public Collection<ResourceInfo> getRelatedResources(String resourcePath) {
    return Arrays.asList(
        new ResourceInfo("/content/my-site/related-page-to-publish", "cq:Page"),
        new ResourceInfo("/content/my-site/other-related-page-to-publish", "cq:Page")
    );
  }
}
