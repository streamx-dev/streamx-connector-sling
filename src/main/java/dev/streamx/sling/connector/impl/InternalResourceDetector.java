package dev.streamx.sling.connector.impl;

final class InternalResourceDetector {

  private InternalResourceDetector() {
    // no instances
  }

  static boolean isInternalResource(String testedResourcePath, String mainResourcePath) {
    return testedResourcePath.startsWith(mainResourcePath + "/");
  }
}
