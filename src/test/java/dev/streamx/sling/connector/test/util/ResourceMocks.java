package dev.streamx.sling.connector.test.util;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import javax.jcr.Node;
import javax.jcr.nodetype.NodeType;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;

public final class ResourceMocks {

  private static final String SLING_FOLDER = JcrResourceConstants.NT_SLING_FOLDER;
  private static final String CQ_PAGE = "cq:Page";
  private static final String DAM_ASSET = "dam:Asset";

  private ResourceMocks() {
    // no instances
  }

  public static Resource createFolderResourceMock() throws Exception {
    return createResourceMock(SLING_FOLDER);
  }

  public static Resource createPageResourceMock() throws Exception {
    return createResourceMock(CQ_PAGE);
  }

  public static Resource createAssetResourceMock() throws Exception {
    return createResourceMock(DAM_ASSET);
  }

  private static Resource createResourceMock(String primaryNodeType) throws Exception {
    Resource resourceMock = mock(Resource.class);
    Node nodeMock = mock(Node.class);
    NodeType nodeTypeMock = mock(NodeType.class);

    doReturn(primaryNodeType).when(nodeTypeMock).getName();
    doReturn(nodeTypeMock).when(nodeMock).getPrimaryNodeType();
    doReturn(nodeMock).when(resourceMock).adaptTo(Node.class);
    return resourceMock;
  }
}