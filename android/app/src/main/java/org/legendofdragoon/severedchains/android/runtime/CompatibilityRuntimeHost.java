package org.legendofdragoon.severedchains.android.runtime;

import android.content.Context;

/** First runtime implementation; the ARM64 JRE 25 bridge is added in the next milestone. */
public final class CompatibilityRuntimeHost implements RuntimeHost {
  @Override
  public String describe(final GamePaths paths) {
    if (paths.importedDiscCount() < 4) {
      return "Import all four disc images before launching.";
    }
    return "Disc images are ready. Install the ARM64 JRE 25 runtime to launch.";
  }

  @Override
  public Result start(final Context context, final GamePaths paths) {
    if (paths.importedDiscCount() < 4) {
      return new Result(false, "Four disc images are required before launch.");
    }
    return new Result(false, "The game files are ready, but the ARM64 JRE 25 runtime has not been installed yet.");
  }
}
