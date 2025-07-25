package dev.streamx.sling.connector.testing.sling.event.jobs;

import java.util.Calendar;
import java.util.Map;
import java.util.Set;
import org.apache.sling.event.jobs.Job;

public class FakeJob implements Job {

  private static final UnsupportedOperationException NOT_IMPLEMENTED_YET = new UnsupportedOperationException("Not implemented yet");

  private final String topic;
  private final Map<String, Object> properties;

  public FakeJob(String topic, Map<String, Object> properties) {
    this.topic = topic;
    this.properties = properties;
  }

  @Override
  public String getTopic() {
    return topic;
  }

  @Override
  public String getId() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public Object getProperty(String propertyName) {
    return properties.get(propertyName);
  }

  @Override
  public Set<String> getPropertyNames() {
    return properties.keySet();
  }

  @Override
  public <T> T getProperty(String propertyName, Class<T> aClass) {
    return (T) properties.get(propertyName);
  }

  @Override
  public <T> T getProperty(String propertyName, T defaultValue) {
    return (T) properties.getOrDefault(propertyName, defaultValue);
  }

  @Override
  public int getRetryCount() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public int getNumberOfRetries() {
    return Integer.MAX_VALUE;
  }

  @Override
  public String getQueueName() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public String getTargetInstance() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public Calendar getProcessingStarted() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public Calendar getCreated() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public String getCreatedInstance() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public JobState getJobState() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public Calendar getFinishedDate() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public String getResultMessage() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public String[] getProgressLog() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public int getProgressStepCount() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public int getFinishedProgressStep() {
    throw NOT_IMPLEMENTED_YET;
  }

  @Override
  public Calendar getProgressETA() {
    throw NOT_IMPLEMENTED_YET;
  }

  // custom methods
  public boolean hasProperty(String name, Object value) {
    return getPropertyNames().contains(name) && getProperty(name).equals(value);
  }

  @Override
  public String toString() {
    return topic + " " + properties;
  }
}
