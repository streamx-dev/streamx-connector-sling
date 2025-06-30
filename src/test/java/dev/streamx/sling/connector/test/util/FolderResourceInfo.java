package dev.streamx.sling.connector.test.util;

import dev.streamx.sling.connector.ResourceInfo;
import java.util.Map;
import org.apache.jackrabbit.JcrConstants;

public class FolderResourceInfo extends ResourceInfo {

  public FolderResourceInfo(String path) {
    super(path, Map.of(JcrConstants.JCR_PRIMARYTYPE, "sling:Folder"));
  }
}