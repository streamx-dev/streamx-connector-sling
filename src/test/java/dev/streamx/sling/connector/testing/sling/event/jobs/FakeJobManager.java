package dev.streamx.sling.connector.testing.sling.event.jobs;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobBuilder;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.Queue;
import org.apache.sling.event.jobs.ScheduledJobInfo;
import org.apache.sling.event.jobs.Statistics;
import org.apache.sling.event.jobs.TopicStatistics;
import org.apache.sling.event.jobs.consumer.JobExecutor;

public class FakeJobManager implements JobManager {

  private static final UnsupportedOperationException NOT_IMPLEMENTED_YET = new UnsupportedOperationException("Not implemented yet");

  private final List<JobExecutor> executors;
  private final List<FakeJob> jobQueue = new LinkedList<>();
  private final List<FakeJob> processedJobs = new LinkedList<>();

  public FakeJobManager(List<JobExecutor> executors) {
    this.executors = new ArrayList<>(executors);
  }

  @Override
  public Statistics getStatistics() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public Iterable<TopicStatistics> getTopicStatistics() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public Queue getQueue(String s) {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public Iterable<Queue> getQueues() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public Job addJob(String topic, Map<String, Object> properties) {
    FakeJob fakeJob = new FakeJob(topic, properties);
    jobQueue.add(fakeJob);
    return fakeJob;
  }

  @Override
  public Job getJobById(String s) {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public boolean removeJobById(String s) {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public Job getJob(String s, Map<String, Object> map) {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public Collection<Job> findJobs(QueryType queryType, String s, long l, Map<String, Object>... maps) {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public void stopJobById(String s) {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public Job retryJobById(String s) {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public JobBuilder createJob(String s) {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public Collection<ScheduledJobInfo> getScheduledJobs() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public Collection<ScheduledJobInfo> getScheduledJobs(String s, long l, Map<String, Object>... maps) {
    throw NOT_IMPLEMENTED_YET;
  }

  /*
   *  CUSTOM METHODS
   */

  public List<FakeJob> getJobQueue() {
    return jobQueue;
  }

  public void processAllJobs() {
    while (!jobQueue.isEmpty()) {
      FakeJob fakeJob = jobQueue.remove(0);
      for (JobExecutor executor : executors) {
        executor.process(fakeJob, new FakeJobExecutionContext());
      }
      processedJobs.add(fakeJob);
    }
  }

  public Job popLastJob() {
    FakeJob lastJob = Iterables.getLast(jobQueue);
    jobQueue.remove(lastJob);
    return lastJob;
  }

  public int getProcessedJobsCount() {
    return processedJobs.size();
  }
}
