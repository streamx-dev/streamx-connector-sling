package dev.streamx.sling.connector.impl;

import dev.streamx.sling.connector.ResourceData;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.PublishData;
import dev.streamx.sling.connector.UnpublishData;
import dev.streamx.sling.connector.testing.handlers.Page;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.apache.sling.api.uri.SlingUri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SamplePagePublicationHandler implements PublicationHandler<Page> {

  private static final String SAMPLE_PAGE_PATH = "/content/sample-page";
  private static final Logger LOG = LoggerFactory.getLogger(SamplePagePublicationHandler.class);

  @Override
  public String getId() {
    return this.getClass().getSimpleName();
  }

  @Override
  public boolean canHandle(ResourceData resourceData) {
    SlingUri slingUri = resourceData.uriToIngest();
    boolean canHandle = slingUri.toString().startsWith(SAMPLE_PAGE_PATH);
    LOG.trace("Can handle {}: {}", slingUri, canHandle);
    return canHandle;
  }

  @Override
  public PublishData<Page> getPublishData(String resourcePath) {
    assert resourcePath.equals(SAMPLE_PAGE_PATH) :
        "Only sample page is supported: " + SAMPLE_PAGE_PATH;
    return new PublishData<>(
        resourcePath + ".html",
        "pages",
        Page.class,
        getPageModel()
    );
  }


  private Page getPageModel() {
    try {
      String sampleHtml = IOUtils.resourceToString(
          "sample-page.html", StandardCharsets.UTF_8, this.getClass().getClassLoader()
      );
      return new Page(sampleHtml);
    } catch (IOException exception) {
      throw new UncheckedIOException("Cannot create page model", exception);
    }
  }

  @Override
  public UnpublishData<Page> getUnpublishData(String resourcePath) {
    assert resourcePath.equals(SAMPLE_PAGE_PATH) :
        "Only sample page is supported: " + SAMPLE_PAGE_PATH;
    return new UnpublishData<>(resourcePath + ".html", "pages", Page.class);
  }
}
