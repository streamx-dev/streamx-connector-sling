package dev.streamx.sling.connector.testing.sling.event.jobs;

class UnsupportedExceptionHelper {

  static <T> T notImplementedYet() {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
