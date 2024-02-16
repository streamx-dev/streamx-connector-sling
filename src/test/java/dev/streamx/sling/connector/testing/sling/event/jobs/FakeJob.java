package dev.streamx.sling.connector.testing.sling.event.jobs;

import java.util.Calendar;
import java.util.Map;
import java.util.Set;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.event.jobs.Job;

public class FakeJob implements Job {

  private final String topic;
  private final ValueMap properties;

  public FakeJob(String topic, Map<String, Object> properties) {
    this.topic = topic;
    this.properties = new ValueMapDecorator(properties);
  }

  @Override
  public String getTopic() {
    return topic;
  }

  @Override
  public String getId() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Object getProperty(String propertyName) {
    return properties.get(propertyName);
  }

  @Override
  public Set<String> getPropertyNames() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public <T> T getProperty(String propertyName, Class<T> aClass) {
    return properties.get(propertyName, aClass);
  }

  @Override
  public <T> T getProperty(String propertyName, T defaultValue) {
    return properties.get(propertyName, defaultValue);
  }

  @Override
  public int getRetryCount() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public int getNumberOfRetries() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public String getQueueName() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public String getTargetInstance() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Calendar getProcessingStarted() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Calendar getCreated() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public String getCreatedInstance() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public JobState getJobState() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Calendar getFinishedDate() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public String getResultMessage() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public String[] getProgressLog() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public int getProgressStepCount() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public int getFinishedProgressStep() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public Calendar getProgressETA() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
