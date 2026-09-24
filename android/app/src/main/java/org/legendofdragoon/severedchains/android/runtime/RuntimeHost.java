package org.legendofdragoon.severedchains.android.runtime;

import android.content.Context;

/** Launch boundary shared by the first compatibility host and a later native engine host. */
public interface RuntimeHost {
  String describe(GamePaths paths);

  Result start(Context context, GamePaths paths);

  record Result(boolean started, String message) { }
}
