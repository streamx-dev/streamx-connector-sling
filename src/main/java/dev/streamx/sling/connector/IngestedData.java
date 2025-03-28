package dev.streamx.sling.connector;

import java.util.Map;
import org.apache.sling.api.uri.SlingUri;

/**
 * Data that is supposed to be ingested by StreamX REST Ingestion Service.
 */
public interface IngestedData {

  /**
   * {@link PublicationAction} to be performed on the {@link IngestedData}.
   *
   * @return {@link PublicationAction} to be performed on the {@link IngestedData}
   */
  PublicationAction ingestionAction();

  /**
   * {@link SlingUri} that identifies the {@link IngestedData}.
   *
   * @return {@link SlingUri} that identifies the {@link IngestedData}
   */
  SlingUri uriToIngest();

  /**
   * Additional properties that are associated with the {@link IngestedData}. The returned
   * properties do not include the {@link #ingestionAction()} and {@link #uriToIngest()}.
   *
   * @return additional properties that are associated with the {@link IngestedData}.
   */
  Map<String, Object> properties();
}
