package dev.streamx.sling.connector;

import java.util.Collection;

public interface RelatedResourcesSelector {

  boolean canProcess(String resourcePath);

  Collection<RelatedResource> getRelatedResources(String resourcePath, PublicationAction action) throws StreamxPublicationException;

}
