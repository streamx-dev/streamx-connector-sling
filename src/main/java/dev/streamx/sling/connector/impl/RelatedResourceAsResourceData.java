package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.ResourceData;
import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.RelatedResource;
import dev.streamx.sling.connector.util.DefaultSlingUriBuilder;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.uri.SlingUri;

class RelatedResourceAsResourceData implements ResourceData {

  private final RelatedResource relatedResource;
  private final Supplier<SlingUri> uriToIngest;

  RelatedResourceAsResourceData(
      RelatedResource relatedResource, ResourceResolverFactory resourceResolverFactory
  ) {
    this.relatedResource = relatedResource;
    this.uriToIngest = () -> new DefaultSlingUriBuilder(
        relatedResource.getResourcePath(), resourceResolverFactory
    ).build();
  }

  @Override
  public PublicationAction ingestionAction() {
    return relatedResource.getAction();
  }

  @Override
  public SlingUri uriToIngest() {
    return uriToIngest.get();
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of();
  }
}
