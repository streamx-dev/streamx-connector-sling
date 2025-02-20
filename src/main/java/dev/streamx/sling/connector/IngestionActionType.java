package dev.streamx.sling.connector;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Type of ingestion action executed by StreamX REST Ingestion Service.
 */
public enum IngestionActionType {

  PUBLISH,
  UNPUBLISH;

  /**
   * Returns the {@link Optional} containing the {@link IngestionActionType} corresponding to the
   * specified {@link String}, ignoring case. If the specified {@link String} does not match any
   * {@link IngestionActionType}, an empty {@link Optional} is returned.
   *
   * @param stringRepresentation {@link String} representation of the requested
   *                             {@link IngestionActionType}
   * @return {@link Optional} containing the {@link IngestionActionType} corresponding to the
   * specified {@link String}
   */
  public static Optional<IngestionActionType> of(String stringRepresentation) {
    return Stream.of(values())
        .filter(
            ingestionActionType -> ingestionActionType.toString()
                .equalsIgnoreCase(stringRepresentation)
        ).findFirst();
  }
}
