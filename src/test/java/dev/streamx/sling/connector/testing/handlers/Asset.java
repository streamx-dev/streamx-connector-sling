package dev.streamx.sling.connector.testing.handlers;

public class Asset {

  private final String data;

  public Asset(String data) {
    this.data = data;
  }

  @Override
  public String toString() {
    return "Asset: " + data;
  }
}
