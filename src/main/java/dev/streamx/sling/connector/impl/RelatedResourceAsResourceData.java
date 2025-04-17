package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.RelatedResource;
import dev.streamx.sling.connector.ResourceData;

class RelatedResourceAsResourceData implements ResourceData {

  private final RelatedResource relatedResource;

  RelatedResourceAsResourceData(RelatedResource relatedResource) {
    this.relatedResource = relatedResource;
  }

  @Override
  public String resourcePath() {
    return relatedResource.getResourcePath();
  }

}
