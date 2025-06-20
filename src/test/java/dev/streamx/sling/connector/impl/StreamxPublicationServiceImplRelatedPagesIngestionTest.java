package dev.streamx.sling.connector.impl;

import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_ACTION;
import static dev.streamx.sling.connector.impl.PublicationJobExecutor.PN_STREAMX_PATH;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.RelatedResourcesSelector;
import dev.streamx.sling.connector.ResourceInfo;
import dev.streamx.sling.connector.handlers.resourcepath.ResourcePathPublicationHandler;
import dev.streamx.sling.connector.handlers.resourcepath.ResourcePathPublicationHandlerConfig;
import dev.streamx.sling.connector.selectors.content.ResourceContentRelatedResourcesSelector;
import dev.streamx.sling.connector.selectors.content.ResourceContentRelatedResourcesSelectorConfig;
import dev.streamx.sling.connector.test.util.PageResourceInfo;
import dev.streamx.sling.connector.testing.handlers.Page;
import dev.streamx.sling.connector.testing.handlers.PagePublicationHandler;
import dev.streamx.sling.connector.testing.sling.event.jobs.FakeJobManager;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionContext.ResultBuilder;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SlingContextExtension.class)
class StreamxPublicationServiceImplRelatedPagesIngestionTest {

  private static final String PAGE = "/content/my-site/en/us/page-1";
  private static final String RELATED_PAGE = "/content/my-site/en/us/related-page-1";

  private static final String PAGE_JSON_RESOURCE_FILE_PATH = "src/test/resources/page.json";

  private final SlingContext slingContext = new SlingContext(ResourceResolverType.JCR_OAK);
  private final ResourceResolver resourceResolver = spy(slingContext.resourceResolver());
  private final ResourceResolverFactory resourceResolverFactory = mock(ResourceResolverFactory.class);
  private final FakeJobManager jobManager = new FakeJobManager(Collections.emptyList());
  private final StreamxPublicationServiceImpl publicationService = new StreamxPublicationServiceImpl();
  private final JobExecutionContext jobExecutionContext = mock(JobExecutionContext.class);

  // resource path + content
  private final Map<String, String> allTestPages = new LinkedHashMap<>();

  private final String page = registerPage(
      PAGE,
      "<html include path='" + RELATED_PAGE + ".html' />"
  );

  private final String relatedPage = registerPage(
      RELATED_PAGE,
      "<html />"
  );

  private final SlingRequestProcessor requestProcessor = (HttpServletRequest request, HttpServletResponse response, ResourceResolver resourceResolver) -> {
    String requestURI = request.getRequestURI();
    assertThat(allTestPages.keySet()).contains(requestURI);
    response.setContentType("text/html");
    response.getWriter().write(allTestPages.get(requestURI));
  };

  private final ResourceContentRelatedResourcesSelectorConfig relatedResourcesConfig = new ResourceContentRelatedResourcesSelectorConfig() {

    @Override
    public Class<? extends Annotation> annotationType() {
      return ResourceContentRelatedResourcesSelectorConfig.class;
    }

    @Override
    public String[] references_search$_$regexes() {
      return new String[]{
          "(/content/.+\\.html)"
      };
    }

    @Override
    public String references_exclude$_$from$_$result_regex() {
      return "";
    }

    @Override
    public String resource$_$path_postfix$_$to$_$append() {
      return ".html";
    }

    @Override
    public String resource_required$_$path_regex() {
      return ".*";
    }

    @Override
    public String resource_required$_$primary$_$node$_$type_regex() {
      return ".*";
    }
  };

  private final ResourcePathPublicationHandler<Page> pageResourcePathPublicationHandler = new ResourcePathPublicationHandler<>(resourceResolverFactory, requestProcessor) {

    @Override
    public ResourcePathPublicationHandlerConfig configuration() {
      return new ResourcePathPublicationHandlerConfig() {
        @Override
        public String resourcePathRegex() {
          return ".*related-page.*";
        }

        @Override
        public String channel() {
          return "pages";
        }

        @Override
        public boolean isEnabled() {
          return true;
        }
      };
    }

    @Override
    public Class<Page> modelClass() {
      return Page.class;
    }

    @Override
    public Page model(InputStream inputStream) {
      try {
        return new Page(IOUtils.toString(inputStream, UTF_8));
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }

    @Override
    public String getId() {
      return "pageResourcePathPublicationHandler";
    }
  };

  @BeforeEach
  void setup() throws Exception {
    doReturn(resourceResolver).when(resourceResolverFactory).getAdministrativeResourceResolver(null);
    doNothing().when(resourceResolver).close();

    slingContext.registerService(StreamxClientConfig.class, new FakeStreamxClientConfig("any", Collections.emptyList()));

    StreamxClientStore streamxClientStore = mock(StreamxClientStoreImpl.class);
    doReturn(List.of(mock(StreamxInstanceClient.class))).when(streamxClientStore).getForResource(anyString());
    slingContext.registerInjectActivateService(streamxClientStore);

    slingContext.registerService(SlingRequestProcessor.class, requestProcessor);

    var selector = new ResourceContentRelatedResourcesSelector(relatedResourcesConfig, requestProcessor, resourceResolverFactory);
    slingContext.registerService(RelatedResourcesSelector.class, selector);
    slingContext.registerInjectActivateService(new RelatedResourcesSelectorRegistry());

    slingContext.registerService(PublicationHandler.class, new PagePublicationHandler(resourceResolver));
    slingContext.registerService(PublicationHandler.class, pageResourcePathPublicationHandler);
    slingContext.registerInjectActivateService(new PublicationHandlerRegistry());

    doReturn(mock(ResultBuilder.class)).when(jobExecutionContext).result();
    slingContext.registerService(JobManager.class, jobManager);

    slingContext.registerInjectActivateService(publicationService);
  }

  @Test
  void shouldPublishRelatedPageOnlyOnce() throws Exception {
    // when
    publishPages(page, relatedPage);

    // then
    assertThat(jobManager.getJobQueue())
        .hasSize(2)
        .allMatch(job -> job.getTopic().equals("dev/streamx/publications"))
        .allMatch(job -> job.hasProperty(PN_STREAMX_ACTION, PublicationAction.PUBLISH.name()))
        .extracting(job -> job.getProperty(PN_STREAMX_PATH))
        .containsExactly(PAGE, RELATED_PAGE);
  }

  private String registerPage(String pageResourcePath, String content) {
    allTestPages.put(pageResourcePath + ".html", content);
    slingContext.load().json(PAGE_JSON_RESOURCE_FILE_PATH, pageResourcePath);
    return pageResourcePath;
  }

  private void publishPages(String... resourcePaths) throws Exception {
    List<ResourceInfo> resourcesToIngest = Arrays.stream(resourcePaths).map(PageResourceInfo::new).collect(Collectors.toList());
    publicationService.publish(resourcesToIngest);
    publicationService.process(jobManager.popLastJob(), jobExecutionContext);
  }
}
