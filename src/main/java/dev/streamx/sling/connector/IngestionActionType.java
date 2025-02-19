package dev.streamx.sling.connector;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Type of ingestion action executed by {@link StreamXIngestion}.
 */
public enum IngestionActionType {

  /**
   * Action that requests to ensure the presence of the data in the target system.
   */
  PUBLISH,

  /**
   * Action that requests to ensure the absence of the data in the target system.
   */
  UNPUBLISH;

  /**
   * Returns the {@link Optional} containing the {@link IngestionActionType} corresponding to the
   * specified {@link String}, ignoring case. If the specified {@link String} does not match any
   * {@link IngestionActionType}, an empty {@link Optional} is returned.
   *
   * @param stringRepresentation {@link String} representation of the requested
   *                             {@link IngestionActionType}
   * @return {@link Optional} containing the {@link IngestionActionType} corresponding to the
   *         specified {@link String}
   */
  public static Optional<IngestionActionType> of(String stringRepresentation) {
    return Stream.of(values())
        .filter(
            ingestionActionType -> ingestionActionType.toString()
                .equalsIgnoreCase(stringRepresentation)
        ).findFirst();
  }
}
