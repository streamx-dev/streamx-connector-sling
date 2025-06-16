package dev.streamx.sling.connector.test.util;

import dev.streamx.sling.connector.ResourceInfo;

public class AssetResourceInfo extends ResourceInfo {

  public AssetResourceInfo(String path) {
    super(path, "dam:Asset");
  }
}