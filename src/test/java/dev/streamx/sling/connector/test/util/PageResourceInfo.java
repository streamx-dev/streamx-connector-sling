package dev.streamx.sling.connector.test.util;

import dev.streamx.sling.connector.ResourceInfo;

public class PageResourceInfo extends ResourceInfo {

  public PageResourceInfo(String path) {
    super(path, "cq:Page");
  }
}