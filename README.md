# Horromodsmc — *Hollow*

![Build](https://github.com/Qynl/Horromodsmc/actions/workflows/build.yml/badge.svg)

> Something stalks the dark.

**Hollow** is a Fabric horror mod for **Minecraft 1.21.1**. It adds a lurking
stalker, a creeping fear system with a live vignette HUD, and the tools you
need to survive the night. Every push is compiled by GitHub Actions — grab
the built jar from the workflow's artifacts.

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
- **Pale Watcher** — a rare (~10%) bone-white variant with red-ringed void
  eyes. Bolder: it tolerates being stared at far longer.

### Dread
A hidden fear value (0–100) for every survival player. It rises at night, in
darkness, and when The Watcher is near. It drains in bright light and near
**Hallowed Lanterns**. As it climbs, the screen darkens with a slow pulsing
vignette and the world starts to feel wrong:

| Dread | Effect |
|------:|--------|
| 25+ | Distant whispers and a chill: *"You feel watched..."* |
| 45+ | Your torches start snuffing themselves out |
| 55+ | Footsteps behind you. Nothing there. Smoke breathes off the ground |
| 70+ | Silent **Apparitions** appear at the edge of your vision |
| 85+ | The Watcher itself manifests nearby |

Dying or rejoining lets you catch your breath. Creative and spectator are
immune.

### Items & Blocks
- **Shadow Fragment** — a sliver of solidified dark, dropped by The Watcher.
- **Warding Totem** — while it is anywhere in your inventory, The Watcher
  cannot focus on you. But every ward *cracks it*: it endures exactly three
  attempts before crumbling to dust. Craft: bone + shadow fragment +
  glowstone dust.
- **Third Eye** — hold it to see your exact Dread readout and make nearby
  Watchers glow through walls. Craft: ender eye ringed by shadow fragments.
- **Hallowed Lantern** — light level 15. Its glow suppresses hostile spawns,
  calms Dread, and The Watcher refuses to manifest near it. Craft: iron +
  glowstone dust + shadow fragment.
- **Watcher Spawn Egg** — for the brave (and for testing).

### Advancements
A small progression tree: **Hollow** → *Safe Keeping*, *Light Against the
Dark*, *Seer*, and the challenge **Unwatched** (slay a Watcher).

## 🧰 Config

Created at `config/hollow.json` on first launch:

```json
{
  "watcherEnabled": true,
  "dreadEnabled": true,
  "eventsEnabled": true,
  "watcherSpawnWeight": 6,
  "dreadMultiplier": 1.0,
  "paleWatcherChance": 0.1
}
```

## ▶️ Playing

1. Install the [Fabric Loader](https://fabricmc.net/use/) for **1.21.1**.
2. Drop [Fabric API](https://modrinth.com/mod/fabric-api) and the mod jar
   (from the CI artifacts or `./gradlew build`) into `.minecraft/mods/`.
3. Survive the night. Bring a totem. Don't stare.

## 🛠️ Building from source

Requires **JDK 21**.

```bash
# one time: generate the wrapper (the jar is not committed to git)
gradle wrapper --gradle-version 8.10

# run the game with the mod loaded
./gradlew runClient

# build the distributable jar
./gradlew build
# -> build/libs/hollow-1.0.0.jar
```

Or let CI do it: every push runs the `Build` workflow (GitHub Actions) which
compiles with Gradle 8.10 / JDK 21 and uploads `hollow-mod` artifacts.

Versions (all checked against `maven.fabricmc.net`): Minecraft `1.21.1`,
Yarn `1.21.1+build.3`, Fabric Loader `0.19.5`, Fabric API `0.105.0+1.21.1`,
Loom `1.7.4`.

## 🗂️ Project layout

```
.github/workflows/build.yml  CI: compile + upload jar artifact
src/main/java/com/horromods/hollow/
├── Hollow.java              mod entrypoint, payload registration
├── HollowConfig.java        JSON config
├── block/                   Hallowed Lantern
├── dread/                   Dread system, scare events, HUD sync
├── entity/                  Watcher (+Pale), Apparition, goals, registration
├── item/                    fragments, totem, third eye, spawn egg
├── network/                 DreadPayload (server → client)
├── util/                    shared helpers
└── client/                  model, renderers, vignette HUD
src/main/resources/
├── fabric.mod.json
└── assets/hollow/ + data/hollow/   lang, models, textures, recipes,
                                    loot tables, advancements
tools/gen_textures.py        dependency-free pixel-art texture generator
```

## 🎨 Textures

All pixel art is generated procedurally — no image editor needed. Re-generate
any time with `python3 tools/gen_textures.py`.

## License

MIT — see [LICENSE](LICENSE).
