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

The bootstrap app is intentionally a launcher shell. It verifies Android storage and
exposes the two future launch paths; it does not yet bundle or execute the game engine.

## Legal notice

Severed Chains requires the player to provide their own legally obtained Legend of
Dragoon disc images. The Android fork must remain distributed under the repository's
AGPL-3.0 license.
