package dev.streamx.sling.connector.test.util;

import dev.streamx.sling.connector.ResourceInfo;
import java.util.Map;
import org.apache.jackrabbit.JcrConstants;

public class PageResourceInfo extends ResourceInfo {

  public PageResourceInfo(String path) {
    super(path, Map.of(JcrConstants.JCR_PRIMARYTYPE, "cq:Page"));
  }
}