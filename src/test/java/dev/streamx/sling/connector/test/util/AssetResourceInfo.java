package dev.streamx.sling.connector.test.util;

import dev.streamx.sling.connector.ResourceInfo;
import java.util.Map;
import org.apache.jackrabbit.JcrConstants;

public class AssetResourceInfo extends ResourceInfo {

  public AssetResourceInfo(String path) {
    super(path, Map.of(JcrConstants.JCR_PRIMARYTYPE, "dam:Asset"));
  }
}