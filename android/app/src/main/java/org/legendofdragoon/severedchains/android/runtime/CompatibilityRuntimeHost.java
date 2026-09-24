package org.legendofdragoon.severedchains.android.runtime;

import android.content.Context;

/** First runtime implementation; the ARM64 JRE 25 bridge is added in the next milestone. */
public final class CompatibilityRuntimeHost implements RuntimeHost {
  @Override
  public String describe(final GamePaths paths) {
    if (paths.importedDiscCount() < 4) {
      return "Import all four disc images before launching.";
    }
    if (!Jre25Installer.isInstalled(paths)) {
      return "Disc images are ready. Install the ARM64 JRE 25 runtime to launch.";
    }
    return "Disc images and ARM64 JRE 25 are ready. Native bridge: " + NativeRuntimeBridge.inspect(paths);
  }

  @Override
  public Result start(final Context context, final GamePaths paths) {
    if (paths.importedDiscCount() < 4) {
      return new Result(false, "Four disc images are required before launch.");
    }
    if (!Jre25Installer.isInstalled(paths)) {
      return new Result(false, "Install the ARM64 JRE 25 runtime before launch.");
    }
    return new Result(false, "JRE 25 VM probe: " + NativeRuntimeBridge.probe(paths)
        + " The Android renderer/audio port is required before game launch.");
  }
}
