package org.legendofdragoon.severedchains.android.runtime;

import android.view.Surface;

/** JNI bridge for Android's EGL window surface; no desktop SDL/LWJGL libraries are involved. */
public final class NativeEglSurface {
  static {
    System.loadLibrary("scjvmhost");
  }

  private NativeEglSurface() { }

  /** @return an error message, or {@code null} when the surface was initialized. */
  public static native String start(Surface surface);
  public static native void resize(int width, int height);
  public static native void stop();
}
