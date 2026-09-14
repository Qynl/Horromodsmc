# Building & running

The mod targets **Minecraft 1.21.1 / Fabric** and Java 21.

## Prerequisites
- JDK 21
- Internet access to the Fabric / Mojang maven repositories (Loom downloads Minecraft, Yarn
  mappings, Fabric Loader and Fabric API on first build).

## Build
```bash
./gradlew build
```
The jar lands in `build/libs/backrooms-level0-0.1.0.jar`. Copy it (and Fabric API) into your
`mods/` folder.

If you don't have the Gradle wrapper jar, generate it once with a local Gradle:
```bash
gradle wrapper --gradle-version 8.10
```

## Run a dev client
```bash
./gradlew runClient
```

## Version bumps
All versions live in `gradle.properties`. When updating Minecraft, bump the whole set together from
<https://fabricmc.net/develop/>: `minecraft_version`, `yarn_mappings`, `loader_version`,
`fabric_version` (and `loom_version` if needed). Nothing in the mod code is version-pinned beyond
the 1.21.1 API surface.

## What is *not* needed to build
The design-validation tools only need Python 3 with `numpy`, `Pillow` and `imageio-ffmpeg`:
```bash
python3 -m pip install numpy pillow imageio-ffmpeg
python3 tools/layout_preview.py --selftest
python3 tools/gen_textures.py
python3 tools/gen_audio.py
```
Textures and sounds are committed, so regenerating them is optional.
