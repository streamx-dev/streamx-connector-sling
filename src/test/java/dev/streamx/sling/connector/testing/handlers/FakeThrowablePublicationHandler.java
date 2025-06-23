package dev.streamx.sling.connector.testing.handlers;

import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.UnpublishData;
import java.util.Map;

public class FakeThrowablePublicationHandler implements PublicationHandler<String> {

  private static final String CHANNEL = "channel";
  private static final Map<String, String> PUBLICATION_PROPERTIES = Map.of("test-property-name", "test value");

  private boolean throwException = false;
  private boolean throwRuntimeException = false;

  @Override
  public String getId() {
    return "fake-handler";
  }

  @Override
  public boolean canHandle(ResourceInfo resource) {
    return true;
  }

  @Override
  public PublishData<String> getPublishData(String resourcePath) throws StreamxPublicationException {
    process();
    return new PublishData<>(
        resourcePath,
        CHANNEL,
        String.class,
        "Success",
        PUBLICATION_PROPERTIES
    );
  }

  @Override
  public UnpublishData<String> getUnpublishData(String resourcePath) throws StreamxPublicationException {
    process();
    return new UnpublishData<>(
        resourcePath,
        CHANNEL,
        String.class,
        PUBLICATION_PROPERTIES
    );
  }

  public void setThrowException() {
    this.throwException = true;
  }

  public void setThrowRuntimeException() {
    this.throwRuntimeException = true;
  }

  private void process() throws StreamxPublicationException {
    if (throwRuntimeException) {
      throw new RuntimeException();
    }

    if (throwException) {
      throw new StreamxPublicationException("Failure");
    }
  }
}
