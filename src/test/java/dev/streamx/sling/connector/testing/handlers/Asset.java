package dev.streamx.sling.connector.testing.handlers;

public class Asset {

  private final byte[] data;

  public Asset(byte[] data) {
    this.data = data;
  }

  @Override
  public String toString() {
    return "Asset: " + new String(data);
  }
}
