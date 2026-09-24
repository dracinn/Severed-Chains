package org.legendofdragoon.severedchains.android.runtime;

import android.content.Context;

import java.io.File;
import java.io.IOException;

/** Android-owned paths shared by the compatibility and future native runtime hosts. */
public final class GamePaths {
  private final File root;
  private final File discs;
  private final File runtime;
  private final File logs;

  private GamePaths(final File root) {
    this.root = root;
    discs = new File(root, "discs");
    runtime = new File(root, "runtime");
    logs = new File(root, "logs");
  }

  public static GamePaths create(final Context context) {
    final File filesDir = context.getExternalFilesDir(null);
    if (filesDir == null) {
      throw new IllegalStateException("External app storage is unavailable");
    }
    final GamePaths paths = new GamePaths(new File(filesDir, "SeveredChains"));
    paths.ensureDirectories();
    return paths;
  }

  public void ensureDirectories() {
    for (final File directory : new File[] {root, discs, runtime, logs}) {
      if (!directory.exists() && !directory.mkdirs()) {
        throw new IllegalStateException("Could not create " + directory);
      }
    }
  }

  public File discs() { return discs; }
  public File runtime() { return runtime; }
  public File logs() { return logs; }

  public int importedDiscCount() {
    final File[] imported = discs.listFiles((directory, name) -> name.startsWith("disc") && name.endsWith(".bin"));
    return imported == null ? 0 : imported.length;
  }

  public File nextDiscDestination() throws IOException {
    for (int disc = 1; disc <= 4; disc++) {
      final File destination = new File(discs, "disc" + disc + ".bin");
      if (!destination.exists()) {
        return destination;
      }
    }
    throw new IOException("All four disc slots are already filled");
  }
}
