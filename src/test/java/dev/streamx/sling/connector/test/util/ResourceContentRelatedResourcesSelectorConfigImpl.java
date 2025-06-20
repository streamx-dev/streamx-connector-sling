package dev.streamx.sling.connector.test.util;

import dev.streamx.sling.connector.selectors.content.ResourceContentRelatedResourcesSelectorConfig;
import java.lang.annotation.Annotation;

public class ResourceContentRelatedResourcesSelectorConfigImpl implements ResourceContentRelatedResourcesSelectorConfig {

  private String[] references_search$_$regexes = new String[0];
  private String references_exclude$_$from$_$result_regex = "";
  private String resource$_$path_postfix$_$to$_$append = "";
  private String resource_required$_$path_regex = "";
  private String resource_required$_$primary$_$node$_$type_regex = "";
  private String related$_$resource_processable$_$path_regex = "";

  public ResourceContentRelatedResourcesSelectorConfigImpl withReferencesSearchRegexes(String... values) {
    this.references_search$_$regexes = values;
    return this;
  }

  public ResourceContentRelatedResourcesSelectorConfigImpl withReferencesExcludeFromResultRegex(String value) {
    this.references_exclude$_$from$_$result_regex = value;
    return this;
  }

  public ResourceContentRelatedResourcesSelectorConfigImpl withResourcePathPostfixToAppend(String value) {
    this.resource$_$path_postfix$_$to$_$append = value;
    return this;
  }

  public ResourceContentRelatedResourcesSelectorConfigImpl withResourceRequiredPathRegex(String value) {
    this.resource_required$_$path_regex = value;
    return this;
  }

  public ResourceContentRelatedResourcesSelectorConfigImpl withResourceRequiredPrimaryNodeTypeRegex(String value) {
    this.resource_required$_$primary$_$node$_$type_regex = value;
    return this;
  }

  public ResourceContentRelatedResourcesSelectorConfigImpl withRelatedResourceProcessablePathRegex(String value) {
    this.related$_$resource_processable$_$path_regex = value;
    return this;
  }

  @Override
  public String[] references_search$_$regexes() {
    return references_search$_$regexes;
  }

  @Override
  public String references_exclude$_$from$_$result_regex() {
    return references_exclude$_$from$_$result_regex;
  }

  @Override
  public String resource$_$path_postfix$_$to$_$append() {
    return resource$_$path_postfix$_$to$_$append;
  }

  @Override
  public String resource_required$_$path_regex() {
    return resource_required$_$path_regex;
  }

  @Override
  public String resource_required$_$primary$_$node$_$type_regex() {
    return resource_required$_$primary$_$node$_$type_regex;
  }

  @Override
  public String related$_$resource_processable$_$path_regex() {
    return related$_$resource_processable$_$path_regex;
  }

  @Override
  public Class<? extends Annotation> annotationType() {
    return ResourceContentRelatedResourcesSelectorConfig.class;
  }
}
