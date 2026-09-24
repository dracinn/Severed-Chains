package org.legendofdragoon.severedchains.android.runtime;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** Append-only diagnostics for runtime installation and startup. */
public final class RuntimeLog {
  private static final String FILE_NAME = "runtime.log";

  private RuntimeLog() { }

  public static synchronized void info(final GamePaths paths, final String message) {
    append(paths, "INFO", message, null);
  }

  public static synchronized void error(final GamePaths paths, final String message, final Throwable error) {
    append(paths, "ERROR", message, error);
  }

  public static File file(final GamePaths paths) {
    return new File(paths.logs(), FILE_NAME);
  }

  public static String read(final GamePaths paths) throws IOException {
    try (FileInputStream input = new FileInputStream(file(paths));
         ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      final byte[] buffer = new byte[8192];
      int count;
      while ((count = input.read(buffer)) != -1) {
        output.write(buffer, 0, count);
      }
      return output.toString(StandardCharsets.UTF_8.name());
    }
  }

  private static void append(final GamePaths paths, final String level, final String message, final Throwable error) {
    try (FileOutputStream output = new FileOutputStream(file(paths), true)) {
      output.write((Instant.now() + " " + level + " " + message + "\n").getBytes(StandardCharsets.UTF_8));
      if (error != null) {
        final StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        output.write(trace.toString().getBytes(StandardCharsets.UTF_8));
      }
    } catch (final IOException ignored) {
      // A logging failure must not hide the original runtime error.
    }
  }
}
