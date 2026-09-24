package org.legendofdragoon.severedchains.android.runtime;

import java.io.File;

/** JNI boundary for the installed ARM64 JRE. It deliberately verifies loading before VM creation. */
public final class NativeRuntimeBridge {
  static {
    System.loadLibrary("scjvmhost");
  }

  private NativeRuntimeBridge() { }

  public static String inspect(final GamePaths paths) {
    final File jvm = new File(paths.runtime(), "jre25/lib/server/libjvm.so");
    if (!jvm.isFile()) {
      return "libjvm.so is missing";
    }
    return inspectJvm(jvm.getAbsolutePath());
  }

  private static native String inspectJvm(String absoluteJvmPath);
}
