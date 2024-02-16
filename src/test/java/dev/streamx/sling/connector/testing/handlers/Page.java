package dev.streamx.sling.connector.testing.handlers;

public class Page {

  private final String data;

  public Page(String data) {
    this.data = data;
  }

  @Override
  public String toString() {
    return "Page: " + data;
  }
}
