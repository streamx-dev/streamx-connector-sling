package dev.streamx.sling.connector.testing.sling.event.jobs;

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
import org.apache.sling.event.jobs.consumer.JobConsumer;

public class FakeJobManager implements JobManager {

  private final List<JobConsumer> consumers;
  private final List<FakeJob> jobQueue = new LinkedList<>();
  private final List<FakeJob> processedJobs = new LinkedList<>();

  public FakeJobManager(List<JobConsumer> consumers) {
    this.consumers = new ArrayList<>(consumers);
  }

  @Override
  public Statistics getStatistics() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Iterable<TopicStatistics> getTopicStatistics() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Queue getQueue(String s) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Iterable<Queue> getQueues() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Job addJob(String topic, Map<String, Object> properties) {
    FakeJob fakeJob = new FakeJob(topic, properties);
    jobQueue.add(fakeJob);
    return fakeJob;
  }

  @Override
  public Job getJobById(String s) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public boolean removeJobById(String s) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Job getJob(String s, Map<String, Object> map) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Collection<Job> findJobs(QueryType queryType, String s, long l,
      Map<String, Object>... maps) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void stopJobById(String s) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Job retryJobById(String s) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public JobBuilder createJob(String s) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Collection<ScheduledJobInfo> getScheduledJobs() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Collection<ScheduledJobInfo> getScheduledJobs(String s, long l,
      Map<String, Object>... maps) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  /*
   *  CUSTOM METHODS
   */

  public void processAllJobs() {
    while (!jobQueue.isEmpty()) {
      FakeJob fakeJob = jobQueue.remove(0);
      for (JobConsumer consumer : consumers) {
        consumer.process(fakeJob);
      }
      processedJobs.add(fakeJob);
    }
  }

  public int getProcessedJobsCount() {
    return processedJobs.size();
  }
}
