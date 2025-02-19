package dev.streamx.sling.connector;

import java.util.Collection;

/**
 * Search for {@link IngestionData} that is related to some different {@link IngestionData}.
 */
@FunctionalInterface
public interface RelatedDataSearch {

  /**
   * Finds the keys of {@link IngestionData} related to the {@link IngestionData} with the passed
   * key.
   *
   * @param key the key of the {@link IngestionData} for which related data should be found
   * @return {@link Collection} of the keys of {@link IngestionData} related to the
   * {@link IngestionData} with the passed key.
   */
  Collection<String> find(String key);
}
