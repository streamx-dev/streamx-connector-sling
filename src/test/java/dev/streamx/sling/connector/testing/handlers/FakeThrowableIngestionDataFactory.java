package dev.streamx.sling.connector.testing.handlers;

import dev.streamx.sling.connector.IngestionDataFactory;
import dev.streamx.sling.connector.IngestionDataKey;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.StreamxPublicationException;
import dev.streamx.sling.connector.UnpublishData;

public class FakeThrowableIngestionDataFactory implements IngestionDataFactory<String> {

  private boolean throwException = false;
  private boolean throwRuntimeException = false;

  @Override
  public String getId() {
    return "fake-handler";
  }

  @Override
  public boolean canProduce(IngestionDataKey ingestionDataKey) {
    return true;
  }

  @Override
  public PublishData<String> producePublishData(IngestionDataKey ingestionDataKey) throws StreamxPublicationException {
    process();
    return new PublishData<>(ingestionDataKey, () -> "channel", String.class, "Success");
  }

  @Override
  public UnpublishData<String> produceUnpublishData(IngestionDataKey ingestionDataKey) throws StreamxPublicationException {
    process();
    return new UnpublishData<>(ingestionDataKey, () -> "channel", String.class);
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
