# The Backrooms — Levels 0, 1 & 2

A Fabric mod for **Minecraft 1.21.1** that adds a rare tear in the Overworld leading into an
effectively endless, buzzing, yellow **Level 0** — and, deeper still, the damp concrete warehouse
of **Level 1** and the narrow brick-and-steel utility tunnels of **Level 2**. It is built around
psychological horror — isolation, unreliable light and impossible architecture — rather than
jumpscares, with one stalker that hunts by sound.

> "What the hell is this place?" → "I need to find a way out." → "Why does this place keep
> changing?" → "I don't think I'm alone."

## What you get

### The Hole
- A **very rare**, irregular opening in normal terrain (surface *and* caves). It looks like a small
  broken section of the world — a few missing blocks and one floating block that shouldn't be there.
  It is **not** a portal: no frame, no particles, no glow pillar.
- Looking through it shows an **empty pale blue-white space** with no horizon. If you look away and
  back, the view is subtly different.
- Walking in (or using it) is **quiet and unceremonious** — one moment you're touching it, the next
  you're on the carpet.

### Level 0
- An endless interior of faded yellow wallpaper, damp carpet, stained ceiling tiles and fluorescent
  lights, generated from a procedural core (`Level0Layout`) that stitches handcrafted *grammars*
  (rooms, corridors, pillar halls, dark zones, the hub, a rare poolroom) into a connected whole.
- **Reliable-near, unreliable-far lighting**: fixtures work near the arrival point and decay into
  flicker and darkness the deeper you walk. Some lights genuinely fail behind you.
- **No music.** The soundscape is fluorescent buzz, distant hum, drips and rare, deniable one-shots.
- **The horror is quiet**: phantom footsteps behind walls, a figure at the edge of vision that is
  gone when you look, corridors that are longer than you remember. Long stretches, *nothing happens*.
- **Reality drift**: while you are elsewhere, the level slowly re-solves itself, so a room may not be
  where it was. Loaded chunks never change while on screen — the change is only ever discovered.
- **Lived-in clutter**, placed sparsely and deterministically by the layout core: office chairs,
  desks, metal barrels, cardboard boxes, the occasional vending machine, and security cameras hanging
  from the ceiling. Never enough to feel stocked — just enough to feel *abandoned*.

### The Listener
A blind entity that only knows you by sound. It is **not** always present; it wanders, and it only
pursues when it hears you. **Sprinting is loud**, walking is audible up close, and **sneaking is
silent** — a sneaking player simply does not exist to it. It never guarantees a kill: contact hurts
and it recoils, giving you a window to run (loudly) or hide (quietly). After a while it drifts off.

Different world seeds produce different Level 0s.

### Level 1 — "Habitable Zone"
Reached by sinking through a second tear. A vast, humid **concrete warehouse**: wide pillar halls,
tall ceilings and sparse, stuttering lights. The floor carries **puddles of stagnant water**, and
the only resources are **supply crates** and **debris piles** left behind by whoever came before.
Pipes run along the walls. Source: backrooms-wiki.wikidot.com/level-1.

### Level 2 — "Abandoned Utility Halls"
Deeper still: an infinite warren of **narrow brick-and-steel service tunnels** that contort at odd
angles, lit by sparse, uneven fluorescents that leave long dark patches. Heavy **piping** lines the
walls; **machinery**, oil drums and crates clutter the passages. Source:
backrooms-wiki.wikidot.com/level-2.

### The Listener hunts every level
The Listener is not confined to Level 0 — it stalks Levels 1 and 2 as well, so the deeper you go the
less alone you are. Each level runs the same psychological layer (phantom footsteps, dying lights,
glimpses, reality drift) on its own layout.

### Adding further levels
Levels are data + a small `LevelTheme`. A deeper level is a new `LevelTheme` in `BackroomsLevels`,
a `data/backrooms/dimension/levelN.json` + biome, and a `BackroomsBuildFeature(N)` palette — the
generator, entry, drift, horror and lighting are all level-agnostic. See `BackroomsLevels.java`.

## Repository layout

| Path | Purpose |
|------|---------|
| `src/main/java/dev/qynl/backrooms/level0/Level0Layout.java` | The pure, Minecraft-free generator core. |
| `tools/layout_preview.py` | Line-for-line Python port used to validate the design + render plans. |
| `tools/gen_textures.py` / `tools/gen_audio.py` | Deterministically regenerate every PNG / OGG. |
| `tools/validate_assets.py` | Cross-references Java registries against the resource files. |
| `docs/preview/*.png` | Rendered plans of Level 0 (origin, deep, and after drift). |
| `src/test/java/...` | JUnit tests for the shipped core. |

## Verifying the design (no Minecraft needed)

```bash
python3 tools/layout_preview.py --selftest          # connectivity, seams, ratios, drift, lighting decay
python3 tools/layout_preview.py --map out.png        # render a plan
python3 tools/validate_assets.py                     # JSON / texture / sound cross-references
```

`--selftest` executes the real algorithm (the Python port mirrors `Level0Layout` exactly, including
Java's `Random` LCG and 64-bit hashing) and checks, across many seeds, that: the plane is connected,
every district opens onto all four neighbours, the shared edge hash agrees from both sides, the
open/wall ratio stays playable, and the only unreachable pockets are the ones `IMPOSSIBLE` districts
seal **on purpose**.

> `Level0Layout.java` and `tools/layout_preview.py` must be changed together. The selftest will not
> detect drift between them; that is a human obligation.

## Building & playing

See [BUILDING.md](BUILDING.md). In short: `./gradlew build`, drop the jar in `mods/`, and wander far
from spawn until you find a hole. Bring torches. They won't help, but bring them anyway.

## Tuning

`config/backrooms.json` (created on first run) exposes hole rarity, drift interval, and on/off
switches for phantom footsteps, glimpses and failing lights.
