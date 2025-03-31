package dev.streamx.sling.connector;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Action that can be performed on a resource for publication purposes.
 */
public enum PublicationAction {

  /**
   * Ingestion action that should result in putting the data in StreamX.
   */
  PUBLISH,

  /**
   * Ingestion action that should result in removing the data from StreamX.
   */
  UNPUBLISH;

  /**
   * Returns the {@link Optional} containing the {@code PublicationAction} corresponding to the specified
   * {@link String}, ignoring case. If the specified {@link String} does not match any {@code PublicationAction},
   * an empty {@link Optional} is returned.
   *
   * @param stringRepresentation {@link String} representation of the requested
   *                             {@code PublicationAction}
   * @return {@link Optional} containing the {@code PublicationAction} corresponding to the specified {@link String}
   */
  public static Optional<PublicationAction> of(String stringRepresentation) {
    return Stream.of(values())
        .filter(
            publicationAction -> publicationAction.toString().equalsIgnoreCase(stringRepresentation)
        ).findFirst();
  }
}
