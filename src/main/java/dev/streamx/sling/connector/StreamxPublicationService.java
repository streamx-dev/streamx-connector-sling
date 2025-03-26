package dev.streamx.sling.connector;

public interface StreamxPublicationService {

  boolean isEnabled();

  void ingest(IngestedData ingestedData);

}
