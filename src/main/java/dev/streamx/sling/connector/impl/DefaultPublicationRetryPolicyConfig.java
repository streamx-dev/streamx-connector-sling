package dev.streamx.sling.connector.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration interface for the default StreamX publication retry policy.
 * This interface defines the configuration attributes for the retry policy, including
 * the base retry delay, the retry multiplication factor, and the maximal retry delay.
 */
@ObjectClassDefinition(name = "Default StreamX publication retry policy config")
public @interface DefaultPublicationRetryPolicyConfig {

  /**
   * The default base retry delay in milliseconds between job retries.
   */
  int DEFAULT_RETRY_DELAY = 2000;

  /**
   * The default multiplication factor for the retry delay.
   */
  int DEFAULT_RETRY_MULTIPLICATION = 2;

  /**
   * The maximal retry delay in milliseconds between job retries.
   */
  int MAX_RETRY_DELAY = 60000;

  /**
   * Returns the base value of the waiting time in milliseconds between job retries.
   *
   * @return the base retry delay in milliseconds
   */
  @AttributeDefinition(name = "Retry delay", description = "The base value of the waiting time in milliseconds between job retries.")
  int retry_delay() default DEFAULT_RETRY_DELAY;

  /**
   * Returns the multiplication factor for the retry delay.
   *
   * @return the retry delay multiplication factor
   */
  @AttributeDefinition(name = "Retry multiplication", description = "The multiplication factor for the retry delay.")
  int retry_multiplication() default DEFAULT_RETRY_MULTIPLICATION;

  /**
   * Returns the maximal waiting time in milliseconds between job retries.
   *
   * @return the maximal retry delay in milliseconds
   */
  @AttributeDefinition(name = "Maximal retry delay", description = "The maximal waiting time in milliseconds between job retries.")
  int max_retry_delay() default MAX_RETRY_DELAY;

}
