# Horromodsmc — *Hollow*

> Something stalks the dark.

**Hollow** is a Fabric horror mod for **Minecraft 1.21.1**. It adds a lurking
stalker, a creeping fear system, and the tools you need to survive the night.

---

## ☠️ Features

### The Watcher
A towering, gaunt silhouette that spawns in the darkest corners of the
overworld (caves, roofs, moonless nights) — and manifests when your Dread
peaks.

- **It stalks you.** It closes to the edge of your sight, circles, and waits
  for you to turn away before it strikes.
- **Don't stare.** Stare at it for too long and it *blinks* somewhere else —
  usually closer. After too many blinks it loses interest and dissolves.
- **Its presence is felt.** Heartbeats grow louder as it nears; its aura
  applies *Darkness*, and its touch slows and blinds you.
- **Daylight melts it** into smoke.
- Drops **Shadow Fragments** (0–2) on death.

### Dread
A hidden fear value (0–100) for every survival player. It rises at night, in
darkness, and when The Watcher is near. It drains in bright light and near
**Hallowed Lanterns**. As it climbs:

| Dread | Effect |
|------:|--------|
| 25+ | Distant whispers and a chill: *"You feel watched..."* |
| 45+ | Your torches start snuffing themselves out |
| 55+ | Footsteps behind you. Nothing there |
| 70+ | Silent **Apparitions** appear at the edge of your vision |
| 85+ | The Watcher itself manifests nearby |

Dying or rejoining lets you catch your breath. Creative and spectator are
immune.

### Items & Blocks
- **Shadow Fragment** — a sliver of solidified dark, dropped by The Watcher.
- **Warding Totem** — while it is anywhere in your inventory, The Watcher
  cannot focus on you at all. Craft: bone + shadow fragment + glowstone dust.
- **Hallowed Lantern** — light level 15. Its glow suppresses hostile spawns,
  calms Dread, and The Watcher refuses to manifest near it. Craft: iron +
  glowstone dust + shadow fragment.
- **Watcher Spawn Egg** — for the brave (and for testing).

## 🧰 Config

Created at `config/hollow.json` on first launch:

```json
{
  "watcherEnabled": true,
  "dreadEnabled": true,
  "eventsEnabled": true,
  "watcherSpawnWeight": 6
}
```

## ▶️ Playing

1. Install the [Fabric Loader](https://fabricmc.net/use/) for **1.21.1**.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) and the built mod
   jar into `.minecraft/mods/`.
3. Survive the night. Bring a totem.

## 🛠️ Building from source

Requires **JDK 21** and **Gradle 8.8+** (10.x recommended).

```bash
# one time: generate the wrapper (the jar is not committed to git)
gradle wrapper --gradle-version 8.10

# run the game with the mod loaded
./gradlew runClient

# build the distributable jar
./gradlew build
# -> build/libs/hollow-1.0.0.jar
```

Versions (all checked against `maven.fabricmc.net`): Minecraft `1.21.1`,
Yarn `1.21.1+build.3`, Fabric Loader `0.19.5`, Fabric API `0.105.0+1.21.1`,
Loom `1.7.4`.

## 🗂️ Project layout

```
src/main/java/com/horromods/hollow/
├── Hollow.java              mod entrypoint
├── HollowConfig.java        JSON config
├── block/                   Hallowed Lantern
├── dread/                   Dread system & scare events
├── entity/                  Watcher, Apparition, goals, registration
├── item/                    fragments, totem, spawn egg
├── util/                    shared helpers
└── client/                  entity model & renderers
src/main/resources/
├── fabric.mod.json
└── assets/hollow/ + data/hollow/   lang, models, textures, recipes, loot
tools/gen_textures.py        dependency-free pixel-art texture generator
```

## 🎨 Textures

All pixel art is generated procedurally — no image editor needed. Re-generate
any time with `python3 tools/gen_textures.py`.

## License

MIT — see [LICENSE](LICENSE).
