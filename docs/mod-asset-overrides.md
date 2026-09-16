# Loose mod asset overrides

This fork adds a loose asset overlay layer for enabled Severed Chains mods.

## Why

Large model and texture replacements should not need to overwrite the extracted `files/` tree or be packed into a mod JAR. An enabled mod can instead provide a sidecar directory under `mods/` whose contents mirror the extracted game paths.

## Layout

A mod with ID `hd_assets` can use:

```text
mods/
├── hd_assets.jar
└── hd_assets/
    └── files/
        └── characters/
            ├── dart/
            │   ├── portrait.png
            │   └── models/
            │       ├── combat/
            │       │   └── 32
            │       └── dragoon/
            │           └── 32
            └── rose/
                ├── portrait.png
                └── models/
                    ├── combat/
                    │   └── 32
                    └── dragoon/
                        └── 32
```

`hd_assets.jar` only needs to be a normal Severed Chains mod whose `@Mod` ID is `hd_assets`. The large binary assets stay in the sidecar directory.

## Partial directory replacement

Directories are merged file-by-file. This is important for character battle models: an HD pack can replace only model file `32`, while files `0` through `31` continue to come from the retail extraction. The pack therefore does not need to duplicate animation data.

MRG-backed directories are also resolved per real file, so an override can replace an individual MRG member without copying the rest of the archive expansion.

## Precedence

Only currently loaded/enabled mods participate in loose asset resolution.

If multiple enabled mods override the same path, they are applied in ascending mod-ID order and the lexicographically later mod ID wins. Override collisions are logged.

## Minimal companion mod

A content-only pack can use a tiny Java entry point such as:

```java
package example.hdassets;

import org.legendofdragoon.modloader.Mod;
import org.legendofdragoon.modloader.events.EventListener;

@Mod(id = "hd_assets", version = "^3.0.0")
@EventListener
public final class HdAssetsMod {
}
```

The JAR activates the sidecar assets; it does not need to contain those assets.

## Current scope

The overlay layer works for any asset that Severed Chains already loads from the extracted `files/` tree, including native TMD/CContainer battle models, existing TIM textures, PNG portraits, scripts, sounds, and other loose files.

True high-resolution battle textures need a renderer extension rather than a larger TIM. The intended follow-up is an optional PNG replacement next to the logical battle texture, for example:

```text
mods/hd_assets/files/characters/dart/textures/combat.png
mods/hd_assets/files/characters/dart/textures/dragoon.png
```

That PNG path should bypass PS1 VRAM sampling and use normalized UVs while retaining the existing TMD geometry, lighting and animation pipeline.
