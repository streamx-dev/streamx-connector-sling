package dev.streamx.sling.connector.impl;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class SetUtils {

  private SetUtils() {
    // no instances
  }

  static <T, U> Set<U> mapToLinkedHashSet(Collection<T> collection, Function<T, U> mapper) {
    return collection
        .stream()
        .map(mapper)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  static <T, U> Set<U> flattenToLinkedHashSet(Collection<T> collection, Function<T, Collection<U>> flatter) {
    return collection
        .stream()
        .map(flatter)
        .flatMap(Collection::stream)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  static <T> Set<T> flattenToLinkedHashSet(Collection<Set<T>> collection) {
    return collection
        .stream()
        .flatMap(Collection::stream)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
