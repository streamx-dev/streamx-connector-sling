package dev.streamx.sling.connector.testing.handlers;

import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.UnpublishData;

public class FakeThrowablePublicationHandler implements PublicationHandler<String> {

  private boolean throwException = false;
  private boolean throwRuntimeException = false;

  @Override
  public String getId() {
    return "fake-handler";
  }

  @Override
  public boolean canHandle(String resourcePath) {
    return true;
  }

  @Override
  public PublishData<String> getPublishData(String resourcePath) throws StreamxPublicationException {
    process();
    return new PublishData<>(resourcePath, "channel", String.class, "Success");
  }

  @Override
  public UnpublishData<String> getUnpublishData(String resourcePath) throws StreamxPublicationException {
    process();
    return new UnpublishData<>(resourcePath, "channel", String.class);
  }

  public void throwException() {
    this.throwException = true;
  }

  public void throwRuntimeException() {
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
