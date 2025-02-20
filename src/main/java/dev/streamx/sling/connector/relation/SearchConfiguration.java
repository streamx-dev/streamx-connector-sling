package dev.streamx.sling.connector.relation;

interface SearchConfiguration {

  String acceptedExtensionRegex();

  String acceptedMimeTypeRegex();

  String acceptedPathRegex();

}
