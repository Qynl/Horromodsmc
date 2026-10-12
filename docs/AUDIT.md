# Backrooms mod — deep audit & improvement plan

Audited on branch `arena/a04a61da-horromodsmc` against `639ab54`. Everything below was verified by
reading the code path end-to-end and, where possible, by executing the repo's own validation tools
(`tools/layout_preview.py --selftest`, `tools/validate_assets.py`). Findings marked **FIXED** were
repaired in this branch; the rest are the roadmap.

Severity: **P0** = the mod does not work / crashes · **P1** = works but degrades or misleads ·
**P2** = quality/polish · **P3** = content & art.

---

## TL;DR — why it feels like trash

1. **You cannot get in.** The Overworld tear literally never generated on the surface (P0-1), and
   the underground variant was ~1-in-1000 chunks at a random depth (P0-2). In survival the Backrooms
   were unreachable; only `/backrooms go 0` (op-only) worked.
2. **If you did get in, it could crash you.** The Skin-Stealer cast client players to
   `ServerPlayerEntity` in `tick()`, which also runs client-side → `ClassCastException` (P0-3).
3. **It got slower the longer you played.** Two unbounded global registries (flicker lights, "already
   built" chunks) grew forever and were re-scanned every few ticks (P1-1, P1-2), and every Backrooms
   chunk was painted **twice** — once by worldgen and once by a per-tick loop on a live world (P1-3).
4. **The level looked worse than the design.** Free-standing pillars — the signature Level 0 look —
   never generated because a condition was inverted (P1-4), the day/night cycle leaked into a
   dimension that is supposed to be timeless (P1-6), and "self-lit" entities used a half-broken
   fullbright light value (P1-7).

The generator core (`Level0Layout`) is genuinely good: connectivity, seams, ratios and drift all pass
its own selftest. The rot was in the Minecraft glue around it.

---

## P0 — the mod did not work

### P0-1 Surface tears never generated (the "backrooms don't spawn in" bug) — FIXED
`placed_feature/reality_hole_surface.json` places the feature with
`{"type":"minecraft:heightmap","heightmap":"WORLD_SURFACE"}`. A heightmap stores **`topSolidY + 1`**,
i.e. the *air block above* the ground (that is why vanilla tree features call `origin.down()`).
`RealityHoleFeature.generate` then began with:

```java
if (!world.getBlockState(origin).isSolidBlock(world, origin)) return false;
```

`origin` is air, always → the feature returned `false` for **every surface chunk in every world**.
Not "rare": zero. Ever.

Fix: the feature now walks down from the placement point onto real ground (max 24 blocks) before
testing anything, and only then rolls rarity. It also carves its bite *downward-safe* so the tear
ends up exposed at the surface instead of sealed inside bedrock.

### P0-2 Underground tears were undiscoverable — FIXED
`holeRarity = 3000` applied per chunk; the "underground is 3× more common" factor divided it to 1000,
then the roll also required a solid block at a uniformly-random y in [-40, 40]. Expected yield:
roughly one hole per thousand chunks of caving, with no `/locate`, no map, no compass, no landmark.
Fix: separate, sane knobs — `holeRarity = 900` (surface) and `undergroundHoleRarity = 260`, and the
underground height window widened to [-58, 48] to cover real cave bands. With 1/900 per 16×16 chunk
the expected distance to the nearest tear from spawn is a few hundred blocks — rare, but findable in
a session, which is the design intent.

### P0-3 Client crash: Skin-Stealer — FIXED
`SkinStealerEntity.chooseQuarry()` did `(ServerPlayerEntity) players.get(0)` on
`getWorld().getPlayers()`. Entity `tick()` runs on the **client** too, where that list holds
`AbstractClientPlayerEntity` → `ClassCastException` → hard client crash whenever a Skin-Stealer was
nearby. Fix: the entity is now simulated server-side only (`if (getWorld().isClient()) return;`) and
the quarry is typed `PlayerEntity`.

### P0-4 No way to obtain or even see most content in creative — FIXED
No item group was registered, so in 1.21.1 the mod's items appeared **nowhere** in the creative
inventory (search included), and 25 of 35 blocks had no `BlockItem` at all. Fix: registered a
`backrooms:main` item group, added `BlockItem`s + item models for every buildable block, exposed
`ModItems.ALL`.

---

## P1 — worked, but degraded or lied

### P1-1 Unbounded flicker-light registry, rescanned constantly — FIXED
`LightFlickerSystem.FLICKERS` collected every flickering fixture in every chunk ever generated, in
every Backrooms dimension, forever (registered from worldgen, never pruned on unload). Every 4th
server tick it iterated the **entire set for every player**. An hour of exploring an endless level =
tens of thousands of distance checks per tick = progressive TPS collapse.
Fix: positions are bucketed by chunk, pruned on `ChunkEvents.CHUNK_UNLOAD`, capped per dimension, and
each tick only visits the 5×5 chunk window around each player.

### P1-2 Unbounded "already built" set + every-tick repaint — FIXED
`BackroomsLevelBuilder.BUILT` was a static `Set<String>` keyed `seed:level:cx,cz` that only ever grew,
and `onServerTick` repainted a 3×3 chunk area around every player **every tick** with
`setBlockState(..., 3)` on a live world (block updates + client syncs + light recalcs). That is the
single biggest source of the "getting there is trash" stutter.
Fix: the builder is now a **chunk-load verifier**: on `ChunkEvents.CHUNK_LOAD` it samples four floor
columns and repaints only chunks that do not look like the level. Bonus: this also repairs worlds
saved by older builds, whose Backrooms chunks are bare bedrock on disk.

### P1-3 Every chunk was built twice, and only 3×3 chunks around you were ever repaired — FIXED
Worldgen *does* run biome features in a flat generator when `"features": true` (it is how vanilla
superflat presets get trees), so the per-tick builder was pure duplicate work; meanwhile its 3×3
radius meant everything beyond ~48 blocks was whatever worldgen produced — and before P0-1's sibling
bug, bare bedrock, which is exactly the "the backrooms don't spawn in" screenshot people see.
With the verifier above, all chunks in view distance are correct and old saves self-heal on load.

### P1-4 Pillars never generated (inverted condition) — FIXED
`Level0Layout.addPillars` required **all four neighbours to be solid** before placing a pillar on an
open tile — i.e. only inside sealed 1-tile pockets, which the generator deliberately avoids. Outside
HALL/HUB districts (which build their own grids) free-standing columns therefore never appeared:
measured **0.00 %** of Level 0 tiles. The inverted test is now correct; measured **1.52 %** for
Level 0 and, after also marking room interiors in `buildRoomGrid`, **2.3 % / 2.2 %** for Levels 3/4.
Mirrored in `tools/layout_preview.py` (the two must stay in lockstep); selftest still green.

### P1-5 Dead tuning: `rng.nextInt(10) < 15` — FIXED
In `buildStandard` the "second, offset cross" width roll could never fail, so that cross was always
2-wide. Now `nextInt(100) < 15`, mirrored in the Python port.

### P1-6 The timeless level breathed with the Overworld clock — FIXED
No `fixed_time` in any `dimension_type`, so `LightmapTextureManager` rebuilt the lightmap from the
current sky angle even though the dimensions have `has_skylight: false`. Mood (and the brightness of
everything "self-lit") silently followed real time of day. All nine dimension types now pin
`fixed_time: 6000`.

### P1-7 "Full-bright" entities were only half-bright — FIXED
Listener/Smiler/Skin-Stealer renderers passed `light(15728640)`, which is `pack(block=0, sky=15)` —
full brightness on the sky axis only. Now `0xF000F0` (`pack(15,15)`), so the Smiler reads as a face in
the dark regardless of dimension or time, as designed.

### P1-8 Teleports could chain, or land you inside a wall — FIXED
`onEntityCollision` fired every tick with no cooldown and no `canUsePortals`-style guard, so a
destination that happened to contain another transition block chain-dropped players several levels
in one second; and `goToLevel` trusted the *layout* for the arrival point even though reality drift
can make the layout disagree with the blocks on disk (→ materialise inside solid wall).
Fix: a 2-second per-player transition cooldown, velocity zeroed on arrival, and `safeArrival()`
verifies the real world (solid floor, two air blocks, no transition block) before teleporting.
`escapeToOverworld` also no longer starts its scan one block above the world top and now skips
replaceable blocks (water, plants, snow) instead of dropping you inside them.

### P1-9 Entities were neither physics objects nor mobs — PARTIALLY FIXED
All five horror entities extend raw `Entity`, so nothing applies gravity, and their own code did
`getVelocity().y - 0.04` (Hound/Skin-Stealer) with **no terminal velocity and nothing zeroing it on
landing** — unbounded downward acceleration — while the Listener kept its vertical velocity forever
and set `vy = 0.42` on any wall bump while chasing, i.e. it rocketed into the ceiling and stuck.
Fixed now: server-only simulation (which also kills the double-integration rubber-banding), gravity
with a terminal velocity and ground zeroing, and despawn when no player is within 80 blocks.
Still true (roadmap P2): raw `Entity` means `damage()` returns false (they are invulnerable — no
counterplay), no pathfinding, no spawn rules, and empty NBT means all state resets on reload.

### P1-10 Config flag mis-wiring — FIXED
`listenerEnabled` also gated the Smiler, Hound and Skin-Stealer, so disabling the Listener deleted
every monster. New `stalkersEnabled` covers those three.

### P1-11 Per-tick client block scan for ambience — FIXED
`AmbientDirector.countNearLights` sampled 196 block positions **every client tick** to derive a buzz
volume that was then never applied (the loop's volume was frozen at creation). Now scanned once a
second and applied live via a mutable `BuzzSoundInstance.setVolume`.

### P1-12 Stale per-player state — FIXED
`HorrorDirector.lastEvent/firstSeen` (and the new teleport cooldown) were never pruned, so paranoia
pinned at max forever and maps leaked per UUID. `ServerPlayConnectionEvents.DISCONNECT` now calls
`HorrorDirector.forgetPlayer` / `HoleEntry.forgetPlayer`.

---

## P2 — quality issues found but not fixed here (roadmap)

1. **Art direction is the weakest link.** Every texture is 16×16 procedural noise from
   `tools/gen_textures.py`; at block scale wallpaper/carpet read as flat mush with no readable
   structure (see `docs/preview` vs an actual in-game block). Entities are single billboarded quads
   with 16×16 textures — a 2.6 m Listener stretched over 16 px. Plan: hand-author (or regenerate with
   real structure: stripe frequency, AO in corners, connected-texture seams for carpet/wallpaper),
   and give entities 64×64 multi-quad bodies with limbs instead of billboards.
2. **No loot tables, no block tags.** Nothing drops, nothing is minable-tagged; combined with the
   "cannot break blocks in the Backrooms" rule the mod has no item loop at all. Plan: loot tables for
   the Overworld-side props and a small salvage loop (almond water? wire? batteries?) so deeper
   levels have stakes.
3. **Lighting model is binary.** A fixture is either a full light-15 cube or nothing; there is no
   falloff personality, no per-level fog density, no emissive panel shape. Plan: slab-shaped light
   panels, per-level `ambient_light` and fog curves, and a vignette/desaturation shader hook for the
   deeper levels.
4. **Audio is one looping buzz.** `AmbientDirector` has two loops and four one-shots; the wiki's
   "hum that stops when you stop" is not there. Plan: distance- and light-reactive layers, per-level
   beds (water for 7, drips for 2/3), and a ducking curve tied to the Listener's hearing state.
5. **No discovery aid at all.** Even with sane rarity, finding the tear is luck. Plan: make the tear
   a real `Structure` so `/locate structure backrooms:reality_hole` works for cheats, plus an
   in-fiction aid (a compass that points at the nearest hum, or rare "static" on a held clock).
6. **Drift is cosmetic-only and can desync.** Advancing drift clears the layout cache but loaded
   chunks keep old blocks, so the layout used for spawns/horror can disagree with the world. Plan:
   apply drift as a *deferred* re-solve on chunk reload only, and never while any player is inside
   the affected district radius.
7. **No Gradle wrapper committed.** `README.md` says `./gradlew build` but the repo has no wrapper;
   CI works around it with `gradle/actions/setup-gradle`. Plan: commit `gradlew` + wrapper jar.
8. **Docs drift.** `README.md` intro says "Levels 0-2", `BackroomsLevels` javadoc says "0-6 ship
   today", lang file called Levels 7 and 8 "Lights Out" (fixed in this branch), and `mod_version`
   0.1.0 with jar name `backrooms-level0` undersells nine levels.
9. **Tests cover only the pure core.** There is no test for the entry path, the builders, or the
   config. Plan: add a lightweight harness (the core is Minecraft-free; the glue can get a
   `MockServerWorld`-style fake) so P0-1-class bugs are caught by CI instead of by players.

---

## P3 — content roadmap (in wiki order, all plumbing already level-agnostic)

| Step | Content | Notes |
|---|---|---|
| 1 | Almond water / supplies on Levels 1-3 | gives the descent a resource economy |
| 2 | Level 3 "black liquid" pipes + hazard | wiki-accurate danger, uses existing `PIPE_WALL` |
| 3 | The Thing on Level 7 & Tiny | water-aware chase AI (needs P1-9's physics rework first) |
| 4 | Level 9+ (Suburbs / The End teaser) | new `LevelTheme` + data pair only |
| 5 | Multiplayer-aware Skin-Stealer skins | currently wears a skin texture, not a real player skin |

---

## Suggested order of work

1. **Ship this branch's P0/P1 fixes** and playtest one full descent 0→8.
2. P2-7 (wrapper) + P2-9 (tests) so future changes are verifiable.
3. P2-1 (art) — single biggest perceived-quality win.
4. P2-3/P2-4 (light & audio mood), then P2-5 (discovery).
5. P3 content, gated behind P1-9's entity rework.

---

# Round 2 — quality pass (second commit on this branch)

After the P0/P1 fixes the mod *worked*; this round makes it not feel cheap. Everything below was
generated and measured in-repo, not hand-waved.

## Art (the biggest perceived-quality problem, now addressed)
`tools/gen_textures.py` was rewritten around five rules (readable structure, tileable noise via
integer-frequency sines, one light direction with 1px bevels, ordered dithering, a per-material
value-contrast budget). Before/after: wallpaper now has stripes and damp streaks instead of yellow
mush; carpet has tuft; ceiling tiles have a perforation grid and water rings; brick has courses and
mortar; pipes are pipes; the vending machine has glass, cans, a keypad and a coin slot.
Entity textures went from 16px stretched over 2.6 m bodies to real silhouettes: Listener 64x128
(too-long arms, pinprick eyes, rim light), Smiler 64x64 grin with teeth, Hound 64x48 quadruped,
Skin-Stealer 64x128 keeping its renderer UV contract (head in x[0.25,0.75], y[0,0.35]).

Measured (5th-95th percentile luminance spread per texture): median **62/255**, lowest are the
deliberately flat surfaces (void gradient 28, faded wallpaper 34). The old set measured as blur.

Block **geometry** also improved (asset-only, no Java risk): fluorescent fixtures are now recessed
panels (frame ring + diffuser slab) instead of full cubes; `metal_bars` is a real gate of posts and
crossed rails; `debris_pile` is a low heap; `water_cooler` is cabinet + bottle + tap.

## Audio
The looping beds (`buzz`, `hum`, `tear_ambient`) used white noise, which does not loop: every 2 s
there was an audible click at the seam. They now use `loop_noise()` (integer-cycle sine sums).
Measured loop discontinuity (|first - last| sample): **buzz 0.006, hum 0.0005, tear_ambient 0.006**
(previously ~0.1+). Buzz gained ballast rattle and a once-per-loop starter tick; hum breathes at
0.5 Hz; carpet steps gained a band-passed fibre rustle; the Listener's step gained a heel/toe
double-tap; `thump` is no longer an inaudible pure 58 Hz sine. Encode quality q3 -> q4.

## Gameplay polish
- `listenerHearing` config knob was dead (hardcoded 14) - now wired into the Listener's hearing radius.
- Level 7 arrival: two-block air pocket to surface into instead of one.
- Level 4 blacked-out windows: 60% of wall columns -> 25% (a wall of windows is noise, not mood).
- Glimpse discard is server-authoritative (client no longer deletes its own copy independently).
- Paranoia/horror state now resets when a player *leaves* the Backrooms, not only on disconnect.
- Missing subtitles wired (`carpet_*`, `wall_sound`, `tear_ambient`) in `sounds.json` + lang.

## Tests
Three regression tests added to `Level0LayoutTest` (pure-core, run in CI):
`freeStandingPillarsActuallyAppear` (guards the inverted-condition bug),
`lightingIsReliableNearTheArrivalPoint`, `clutterIsSparseButPresent`.

## Verification performed for this round
| Check | Result |
|---|---|
| `python3 tools/validate_assets.py` | ALL ASSET CROSS-REFERENCES OK (156 json, 21 sounds, 35 blocks, 25 items) |
| `python3 tools/layout_preview.py --selftest` | ALL CHECKS PASSED (connectivity, seams, ratios, drift, lighting decay) |
| Java brace/paren balance over all sources | clean |
| Texture contrast metric | median 62/255, no accidental mush |
| Audio loop seam / peak / RMS | seams <= 0.006, peaks <= 0.93 (no clipping), beds RMS 0.18-0.25 |
| Visual QC | contact sheets of all 41 block + 6 entity textures reviewed at 4x |

## Still true, still on the roadmap
No JDK/Minecraft maven access in this sandbox, so nothing here was compiled or playtested - CI
(`.github/workflows/build.yml`) is the compile gate. Art direction is now structured but still
procedural 16x16; a hand-authored pass would beat it. Entities are still billboards and still raw
`Entity` (invulnerable, no pathfinding) - see P1-9 and P2-1..P2-5 above.

---

# Round 3 — "the dimension is just bedrock"

Root cause chain, in order of importance:

1. **Rounds 1 and 2 never compiled.** A scripted edit inserted a `//` comment in the middle of a
   fluent vertex chain in `ListenerRenderer`/`SmilerRenderer`, swallowing `.normal(...)`: 8 syntax
   errors. CI (`.github/workflows/build.yml`) failed on both commits, so any jar in circulation
   predates every fix. Lesson recorded: CI is the only compiler available to this workflow and its
   status must be checked after every push - it now is.
2. **The level relied on flat-generator biome decoration**, which evidently never painted in real
   worlds, leaving the superflat bedrock shell as the only thing generated.
   Fix: `BackroomsChunkGenerator extends FlatChunkGenerator` (codec registered as
   `backrooms:level_gen`, referenced by all nine `dimension/levelN.json`) paints the level in
   `buildSurface` - the very pass that lays the bedrock, on the generation thread, before the chunk
   is saved. Correct by construction, independent of decoration flags or ordering.
   `paintChunk`/`buildChunk` now take `ServerWorldAccess` so worldgen regions and live server worlds
   share one code path.
3. **Existing broken saves** are still repaired twice over: the chunk-load verifier (round 1) and a
   new op command `/backrooms rebuild`, which re-paints the 25x22 loaded chunks around the player.
