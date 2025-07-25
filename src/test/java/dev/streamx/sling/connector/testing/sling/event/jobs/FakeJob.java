package dev.streamx.sling.connector.testing.sling.event.jobs;

import static dev.streamx.sling.connector.testing.sling.event.jobs.UnsupportedExceptionHelper.notImplementedYet;

import java.util.Calendar;
import java.util.Map;
import java.util.Set;
import org.apache.sling.event.jobs.Job;

public class FakeJob implements Job {

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
    return notImplementedYet();
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
    return notImplementedYet();
  }

  @Override
  public int getNumberOfRetries() {
    return Integer.MAX_VALUE;
  }

  @Override
  public String getQueueName() {
    return notImplementedYet();
  }

  @Override
  public String getTargetInstance() {
    return notImplementedYet();
  }

  @Override
  public Calendar getProcessingStarted() {
    return notImplementedYet();
  }

  @Override
  public Calendar getCreated() {
    return notImplementedYet();
  }

  @Override
  public String getCreatedInstance() {
    return notImplementedYet();
  }

  @Override
  public JobState getJobState() {
    return notImplementedYet();
  }

  @Override
  public Calendar getFinishedDate() {
    return notImplementedYet();
  }

  @Override
  public String getResultMessage() {
    return notImplementedYet();
  }

  @Override
  public String[] getProgressLog() {
    return notImplementedYet();
  }

  @Override
  public int getProgressStepCount() {
    return notImplementedYet();
  }

  @Override
  public int getFinishedProgressStep() {
    return notImplementedYet();
  }

  @Override
  public Calendar getProgressETA() {
    return notImplementedYet();
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
