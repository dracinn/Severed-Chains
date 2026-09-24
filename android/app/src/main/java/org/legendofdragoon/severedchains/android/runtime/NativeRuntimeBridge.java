package org.legendofdragoon.severedchains.android.runtime;

import java.io.File;

/** JNI boundary for the installed ARM64 JRE. It deliberately verifies loading before VM creation. */
public final class NativeRuntimeBridge {
  static {
    // The downloaded ARM64 JRE's libjvm.so depends on the NDK shared C++ runtime.
    // Load the APK-packaged copy into the app namespace before loading libjvm.so.
    System.loadLibrary("c++_shared");
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

  public static String probe(final GamePaths paths) {
    final File jre = new File(paths.runtime(), "jre25");
    final File jvm = new File(jre, "lib/server/libjvm.so");
    if (!jvm.isFile()) {
      return "libjvm.so is missing";
    }
    return probeJvm(jvm.getAbsolutePath(), jre.getAbsolutePath());
  }

  private static native String inspectJvm(String absoluteJvmPath);
  private static native String probeJvm(String absoluteJvmPath, String javaHome);
}
