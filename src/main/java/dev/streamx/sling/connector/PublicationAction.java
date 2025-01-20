package dev.streamx.sling.connector;

import java.util.stream.Stream;

/**
 * Action that can be performed on a resource for publication purposes.
 */
public enum PublicationAction {

  PUBLISH,
  UNPUBLISH,
  UNDEFINED;

  /**
   * Returns the {@code PublicationAction} corresponding to the specified {@link String}, ignoring
   * case.
   *
   * @param stringRepresentation {@link String} representation of the requested
   *                             {@code PublicationAction}
   * @return {@code PublicationAction} represented by the specified {@link String}
   */
  public static PublicationAction of(String stringRepresentation) {
    return Stream.of(values())
        .filter(
            publicationAction -> publicationAction.toString().equalsIgnoreCase(stringRepresentation)
        ).findFirst()
        .orElse(PublicationAction.UNDEFINED);
  }
}
