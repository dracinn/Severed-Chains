# Severed Chains Android

This directory is a clean Android fork layer for Severed Chains. It is intentionally
separate from the desktop Gradle project so upstream updates remain straightforward to
merge.

## Launch-first roadmap

The first playable builds will use a compatibility host: Android owns storage, import,
controls, log collection, and lifecycle; the host launches the Java game through an
embedded **ARM64 JRE 25**. This matches the observable behaviour of the reference APK
without reusing its code.

The runtime host is behind a small interface so it can later be replaced with a native
Android engine while retaining the same application UI and user data locations.

## Build

Use a JDK 17+ and Android SDK API 35 to build the launcher. The game itself runs in a
separate ARM64 JRE 25 installed by the compatibility host; the Android launcher must
not attempt to compile the upstream Java 25 game sources into DEX.

```bash
../gradlew -p android :app:assembleDebug
```

## Current status

The debug app imports the player's four disc images into app-owned storage, installs a
checksummed ARM64 JRE 25, and can create and destroy that JVM through a small native
bridge. This provides an on-device verification point for the compatibility runtime.

It intentionally does **not** yet bundle or execute the game engine. Upstream currently
uses desktop LWJGL/SDL and Linux/desktop native libraries; those need an Android
renderer, audio, input, and lifecycle backend before `legend.game.Main` can run on a
phone. The intended Java entry point is `legend.game.Main`.

The shared engine now selects its platform backend with the
`legend.platform.class` system property (desktop defaults to SDL). The future Android
runtime JAR will provide its own `PlatformManager` implementation through that contract;
this prevents Android code from being coupled to the desktop SDL implementation.

## Legal notice

Severed Chains requires the player to provide their own legally obtained Legend of
Dragoon disc images. The Android fork must remain distributed under the repository's
AGPL-3.0 license.
