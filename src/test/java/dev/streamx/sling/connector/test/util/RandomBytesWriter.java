package dev.streamx.sling.connector.test.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;
import javax.servlet.ServletResponse;

public final class RandomBytesWriter {

  private RandomBytesWriter() {
    // no instances
  }

  public static void writeRandomBytes(ServletResponse response, int count) throws IOException {
    response.setContentType("application/octet-stream");
    response.setContentLength(count);
    byte[] randomData = new byte[count];
    new Random().nextBytes(randomData);
    try (OutputStream out = response.getOutputStream()) {
      out.write(randomData);
      out.flush();
    }
  }
}
