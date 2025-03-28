package dev.streamx.sling.connector.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.streamx.sling.connector.IngestedData;
import dev.streamx.sling.connector.PublicationAction;
import dev.streamx.sling.connector.PublicationHandler;
import dev.streamx.sling.connector.handlers.resourcepath.ClientlibsHandler;
import dev.streamx.sling.connector.handlers.resourcepath.ClientlibsHandlerConfig;
import dev.streamx.sling.connector.selectors.content.ResourceContentRelatedResourcesSelector;
import dev.streamx.sling.connector.util.DefaultSlingUriBuilder;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.uri.SlingUri;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionContext.ResultBuilder;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.testing.mock.sling.junit5.SlingContext;
import org.apache.sling.testing.mock.sling.junit5.SlingContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, SlingContextExtension.class})
class StreamxPublicationServiceImplTest {

  private final SlingContext context = new SlingContext();

  @Mock
  private JobManager jobManager;
  private StreamxPublicationServiceImpl streamxPublicationService;

  private static class DefaultRequestProcessor implements SlingRequestProcessor {

    @Override
    public void processRequest(
        HttpServletRequest request, HttpServletResponse response, ResourceResolver resourceResolver
    ) throws IOException {
      String requestURI = request.getRequestURI();
      if (requestURI.equals("/content/sample-page.html")) {
        response.setContentType("text/html");
        String sampleHtml = IOUtils.resourceToString(
            "sample-page.html", StandardCharsets.UTF_8, this.getClass().getClassLoader()
        );
        response.getWriter().write(sampleHtml);
      } else {
        response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      }
    }
  }

  @BeforeEach
  void setup() {
    context.registerService(JobManager.class, jobManager);
    context.registerService(SlingRequestProcessor.class, new DefaultRequestProcessor());
    context.registerService(
        PublicationHandler.class, new ClientlibsHandler(
            Objects.requireNonNull(context.getService(ResourceResolverFactory.class)),
            new DefaultRequestProcessor(),
            new ClientlibsHandlerConfig() {
              @Override
              public Class<? extends Annotation> annotationType() {
                return ClientlibsHandlerConfig.class;
              }

              @Override
              public String clientlibs_path_regexp() {
                return "^/etc\\.clientlibs/.+";
              }

              @Override
              public String publication_channel() {
                return "web-resources";
              }

              @Override
              public boolean enabled() {
                return true;
              }
            }
        )
    );
    context.registerService(PublicationHandler.class, new SamplePagePublicationHandler());
    context.registerInjectActivateService(
        PublicationHandlerRegistry.class
    );
    context.registerInjectActivateService(
        ResourceContentRelatedResourcesSelector.class,
        Map.of(
            "references.search-regexes", new String[]{
                "(/content[^\"'\\s]*\\.coreimg\\.[^\"'\\s]*)",
                "(/[^\"'\\s]*etc\\.clientlibs[^\"'\\s]*)"
            },
            "references.exclude-from-result.regex", ".*\\{\\.width\\}.*",
            "resource-path.postfix-to-append", ".html",
            "resource.required-path.regex", "^/content/.*",
            "resource.required-primary-node-type.regex", ".*"
        )
    );
    context.registerInjectActivateService(RelatedResourcesSelectorRegistry.class);
    StreamxInstanceClient streamxInstanceClient = mock(StreamxInstanceClient.class);
    lenient().when(streamxInstanceClient.getName()).thenReturn("streamx");
    StreamxClientStore streamxClientStore = mock(StreamxClientStore.class);
    lenient().doReturn(List.of(streamxInstanceClient)).when(streamxClientStore)
        .getForResource(anyString());
    context.registerService(StreamxClientStore.class, streamxClientStore);
    streamxPublicationService = context.registerInjectActivateService(
        StreamxPublicationServiceImpl.class
    );

    doAnswer(
        invocation -> {
          String actualTopic = invocation.getArgument(
              NumberUtils.INTEGER_ZERO, String.class
          );
          @SuppressWarnings("unchecked")
          Map<String, Object> jobProps = (
              (Map<String, Object>) invocation.getArgument(
                  NumberUtils.INTEGER_ONE, Map.class
              )
          );
          Job job = mock(Job.class);
          if (actualTopic.equals(JobAsIngestedData.JOB_TOPIC)) {
            JobExecutionContext jobExecutionContext = mock(JobExecutionContext.class);
            ResultBuilder resultBuilder = mock(ResultBuilder.class);
            doReturn(resultBuilder).when(jobExecutionContext).result();
            JobExecutionResult expectedJobExecutionResult = mock(JobExecutionResult.class);
            doReturn(expectedJobExecutionResult).when(resultBuilder).succeeded();
            doReturn(true).when(expectedJobExecutionResult).succeeded();
            when(job.getProperty("streamx.uriToIngest", String.class)).thenReturn(
                (String) Objects.requireNonNull(jobProps.get("streamx.uriToIngest"))
            );
            when(job.getProperty("streamx.ingestionAction", String.class)).thenReturn(
                (String) Objects.requireNonNull(jobProps.get("streamx.ingestionAction"))
            );
            JobExecutionResult actualExecutionResult = streamxPublicationService.process(
                job, jobExecutionContext
            );
            assertTrue(actualExecutionResult.succeeded());
            assertFalse(actualExecutionResult.failed());
            assertFalse(actualExecutionResult.cancelled());
          } else if (actualTopic.equals(PublicationJobExecutor.JOB_TOPIC)) {
            assertEquals(4, jobProps.size());
          }
          return job;
        }
    ).when(jobManager).addJob(anyString(), anyMap());
  }

  @Test
  void test() {
    streamxPublicationService.ingest(samplePage());
    @SuppressWarnings("MagicNumber")
    int expectedNumOfJobs = 12;
    verify(jobManager, times(expectedNumOfJobs)).addJob(anyString(), anyMap());
  }

  private IngestedData samplePage() {
    return new IngestedData() {
      @Override
      public PublicationAction ingestionAction() {
        return PublicationAction.PUBLISH;
      }

      @Override
      public SlingUri uriToIngest() {
        return new DefaultSlingUriBuilder(
            "/content/sample-page",
            Objects.requireNonNull(context.getService(ResourceResolverFactory.class))
        ).build();
      }

      @Override
      public Map<String, Object> properties() {
        return Map.of();
      }
    };
  }
}
