package dev.streamx.sling.connector.testing.sling.event.jobs;

import org.apache.sling.event.impl.jobs.queues.ResultBuilderImpl;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;

public class FakeJobExecutionContext implements JobExecutionContext {

  @Override
  public void asyncProcessingFinished(JobExecutionResult jobExecutionResult) {

  }

  @Override
  public boolean isStopped() {
    return false;
  }

  @Override
  public void initProgress(int i, long l) {

  }

  @Override
  public void incrementProgressCount(int i) {

  }

  @Override
  public void updateProgress(long l) {

  }

  @Override
  public void log(String s, Object... objects) {

  }

  @Override
  public ResultBuilder result() {
    return new ResultBuilderImpl();
  }
}
