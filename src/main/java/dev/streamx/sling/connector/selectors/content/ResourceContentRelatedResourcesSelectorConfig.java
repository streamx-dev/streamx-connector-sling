package dev.streamx.sling.connector.selectors.content;

import javax.jcr.nodetype.NodeType;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi configuration interface that defines how paths are recognized, filtered, and extracted from
 * a {@link Resource}'s textual representation.
 *
 * <p>This configuration is used by {@link ResourceContentRelatedResourcesSelector} to:
 * <ul>
 *   <li>Determine how to search for and extract paths via regex patterns.</li>
 *   <li>Filter out unwanted paths.</li>
 *   <li>Validate whether a {@link Resource} meets certain criteria based on path
 *       and JCR primary node type before being recognized.</li>
 * </ul>
 *
 * <p>The textual representation of a {@link Resource} is obtained by issuing an internal Sling request.
 * If {@link #resource$_$path_postfix$_$to$_$append()} is provided, that postfix is appended to the {@link Resource}
 * path before the request. Then the specified regexes are applied to the response to discover
 * candidate paths, which can subsequently be excluded or accepted based on the provided
 * filtering regexes.
 */
@SuppressWarnings("NewClassNamingConvention")
@ObjectClassDefinition
public @interface ResourceContentRelatedResourcesSelectorConfig {

  /**
   * An array of regex patterns used to search for paths within the textual representation of a
   * {@link Resource}. Each pattern must capture the path in the <em>first capturing group</em>
   * (i.e., {@code group(1)}).
   *
   * @return array of regex patterns used to search for paths within the textual representation of a
   * {@link Resource}
   */
  @AttributeDefinition(
      name = "References Search Regexes",
      description = "Array of regex patterns used to search for paths within "
          + "the textual representation of a Resource.",
      type = AttributeType.STRING
  )
  String[] references_search$_$regexes() default {};

  /**
   * A regex pattern used to exclude certain paths from the final extracted results. Any path
   * matching this pattern will be omitted.
   *
   * @return regex pattern used to exclude certain paths from the final extracted results.
   */
  @AttributeDefinition(
      name = "References Regex to Exclude from Result",
      description = "Regex pattern used to exclude certain paths from the final extracted results.",
      type = AttributeType.STRING,
      defaultValue = StringUtils.EMPTY
  )
  String references_exclude$_$from$_$result_regex() default StringUtils.EMPTY;

  /**
   * Specifies a postfix to append to the {@link Resource} path before performing the internal Sling
   * request to retrieve the text representation of that {@link Resource}. This can help ensure the
   * {@link Resource} responds with the desired format. For example, appending {@code .html} might
   * trigger a specific rendering script.
   *
   * @return postfix to append to the {@link Resource} path before performing the internal Sling
   * request to retrieve the text representation of that {@link Resource}.
   */
  @AttributeDefinition(
      name = "Resource Path Postfix to Append",
      description = "Postfix to append to the Resource path before performing the internal Sling request to retrieve the text representation of that Resource. Specified value must not be prepended with a dot (.).",
      type = AttributeType.STRING,
      defaultValue = StringUtils.EMPTY
  )
  String resource$_$path_postfix$_$to$_$append() default StringUtils.EMPTY;

  /**
   * A regex pattern that the {@link Resource} path must match in order for the {@link Resource} to
   * be considered acceptable. If the path does not match this pattern, it will be skipped.
   *
   * @return a regex pattern that the {@link Resource} path must match.
   */
  @AttributeDefinition(
      name = "Resource Path Regex",
      description = "Regex pattern that the Resource path must match.",
      type = AttributeType.STRING,
      defaultValue = ".*"
  )
  String resource_required$_$path_regex() default ".*";

  /**
   * A regex pattern that the {@link Resource}'s primary {@link NodeType} must match for the
   * {@link Resource} to be considered acceptable.
   *
   * @return regex pattern that the {@link Resource}'s primary {@link NodeType} must match for the
   * {@link Resource} to be considered acceptable.
   */
  @SuppressWarnings("NewMethodNamingConvention")
  @AttributeDefinition(
      name = "Resource Node Primary Type Regex",
      description = "Regex pattern that the Resource's primary Node Type must match for the Resource to be considered acceptable.",
      type = AttributeType.STRING,
      defaultValue = ".*"
  )
  String resource_required$_$primary$_$node$_$type_regex() default ".*";
}
