package dev.streamx.sling.connector.relation;

import dev.streamx.sling.connector.RelatedDataSearch;
import java.util.Collection;

@Configurable(with = SearchConfiguration.class)
public interface FrontEndResourcesSearch extends RelatedDataSearch {

  Collection<FrontEndResource> find(WebPage page);
}
