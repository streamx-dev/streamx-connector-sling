package dev.streamx.sling.connector.test.util;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import javax.jcr.Node;
import javax.jcr.nodetype.NodeType;
import org.apache.sling.api.resource.Resource;

public final class ResourceMocks {

  private ResourceMocks() {
    // no instances
  }

  public static Resource createResourceMock(String primaryNodeType) throws Exception {
    Resource resourceMock = mock(Resource.class);
    Node nodeMock = mock(Node.class);
    NodeType nodeTypeMock = mock(NodeType.class);

    doReturn(primaryNodeType).when(nodeTypeMock).getName();
    doReturn(nodeTypeMock).when(nodeMock).getPrimaryNodeType();
    doReturn(nodeMock).when(resourceMock).adaptTo(Node.class);
    return resourceMock;
  }
}
