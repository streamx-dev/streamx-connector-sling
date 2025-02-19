package dev.streamx.sling.connector.testing.selectors;

import dev.streamx.sling.connector.RelatedDataSearch;
import java.util.Collection;
import java.util.List;

public class RelatedPagesSearch implements RelatedDataSearch {

  @Override
  public Collection<String> find(String key) {
    return List.of("/content/my-site/related-page-to-publish",
        "/content/my-site/related-page-to-unpublish");
  }
}
