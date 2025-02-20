package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.RelatedDataSearch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

@Component(service = RelatedDataSearches.class)
public class RelatedDataSearches {

  @Reference(
      cardinality = ReferenceCardinality.AT_LEAST_ONE,
      policy = ReferencePolicy.DYNAMIC,
      policyOption = ReferencePolicyOption.GREEDY
  )
  private final List<RelatedDataSearch> searches;

  @Activate
  public RelatedDataSearches() {
    searches = new ArrayList<>();
  }

  List<RelatedDataSearch> searches() {
    return Collections.unmodifiableList(searches);
  }

}
