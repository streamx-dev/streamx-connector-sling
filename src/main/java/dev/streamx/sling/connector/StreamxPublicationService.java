package dev.streamx.sling.connector;

/**
 * StreamX REST Ingestion Service.
 */
public interface StreamxPublicationService {

  /**
   * Indicates if the service is enabled or not.
   *
   * @return {@code true} if the service is enabled; {@code false} otherwise
   */
  boolean isEnabled();

  /**
   * Ingests the {@link IngestedData} into StreamX.
   *
   * @param ingestedData {@link IngestedData} to ingest into StreamX
   */
  void ingest(IngestedData ingestedData);

}
