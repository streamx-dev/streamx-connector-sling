package dev.streamx.sling.connector.testing.sling.event.jobs;

import static dev.streamx.sling.connector.testing.sling.event.jobs.UnsupportedExceptionHelper.notImplementedYet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobBuilder;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.Queue;
import org.apache.sling.event.jobs.ScheduledJobInfo;
import org.apache.sling.event.jobs.Statistics;
import org.apache.sling.event.jobs.TopicStatistics;
import org.apache.sling.event.jobs.consumer.JobExecutor;

public class FakeJobManager implements JobManager {

  private final List<JobExecutor> executors;
  private final List<FakeJob> jobQueue = new LinkedList<>();
  private final List<FakeJob> processedJobs = new LinkedList<>();

  public FakeJobManager(List<JobExecutor> executors) {
    this.executors = new ArrayList<>(executors);
  }

  @Override
  public Statistics getStatistics() {
    return notImplementedYet();
  }

  @Override
  public Iterable<TopicStatistics> getTopicStatistics() {
    return notImplementedYet();
  }

  @Override
  public Queue getQueue(String s) {
    return notImplementedYet();
  }

  @Override
  public Iterable<Queue> getQueues() {
    return notImplementedYet();
  }

  @Override
  public Job addJob(String topic, Map<String, Object> properties) {
    FakeJob fakeJob = new FakeJob(topic, properties);
    jobQueue.add(fakeJob);
    return fakeJob;
  }

  @Override
  public Job getJobById(String s) {
    return notImplementedYet();
  }

  @Override
  public boolean removeJobById(String s) {
    return notImplementedYet();
  }

  @Override
  public Job getJob(String s, Map<String, Object> map) {
    return notImplementedYet();
  }

  @Override
  public Collection<Job> findJobs(QueryType type, String topic, long limit, Map<String, Object>... templates) {
    if (templates.length != 1) {
      return notImplementedYet();
    }

    Collection<Job> foundJobs = new LinkedList<>();
    Map<String, Object> properties = templates[0];
    for (Job job : jobQueue) {
      if (job.getTopic().equals(topic) && doesJobHaveProperties(job, properties)) {
        foundJobs.add(job);
      }
    }
    return foundJobs;
  }

  private static boolean doesJobHaveProperties(Job job, Map<String, Object> properties) {
    boolean propertiesMatch = true;
    for (var property : properties.entrySet()) {
      String propertyName = property.getKey();
      Object expectedValue = property.getValue();
      if (!Objects.equals(job.getProperty(propertyName), expectedValue)) {
        propertiesMatch = false;
        break;
      }
    }
    return propertiesMatch;
  }

  @Override
  public void stopJobById(String s) {
    notImplementedYet();
  }

  @Override
  public Job retryJobById(String s) {
    return notImplementedYet();
  }

  @Override
  public JobBuilder createJob(String s) {
    return notImplementedYet();
  }

  @Override
  public Collection<ScheduledJobInfo> getScheduledJobs() {
    return notImplementedYet();
  }

  @Override
  public Collection<ScheduledJobInfo> getScheduledJobs(String s, long l, Map<String, Object>... maps) {
    return notImplementedYet();
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

  public int getProcessedJobsCount() {
    return processedJobs.size();
  }
}
