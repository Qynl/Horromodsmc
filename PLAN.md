# GRID DUEL — Plan

A 2-player-style light cycle deathmatch: **you vs. an AI**, Tron-style, wrapped in an
80s arcade cabinet. Built with **React + Vite**, rendered on an HTML5 canvas with
chunky pixels, scanlines and synthesized retro sound effects.

---

## 1. Core Game

- **Arena**: 64×48 cell grid (640×480 internal canvas, `image-rendering: pixelated`,
  scaled to fit the browser window).
- **Players**: you (cyan cycle) vs. the AI (orange cycle). Each cycle moves one cell
  per tick and leaves a solid **wall of light** behind it.
- **Lose conditions**: crash into your own trail, the enemy trail, or the arena border.
  Head-on collision = draw round.
- **Movement**: absolute steering (Arrow keys or WASD), 180° reversals are blocked.
  Ticks run at a fixed rate (~every 60 ms — fast but readable).
- **Match**: **first to 5 round wins** takes the match. Round intro: `3 · 2 · 1 · GO!`.

## 2. Retro Pixel Look

- Near-black blue grid background, faint grid lines, glowing chunky trails with a
  bright cycle head and slightly darker body cells.
- **CRT arcade dressing**: scanline overlay, vignette, subtle flicker, dark bezel
  frame around the screen like a cabinet.
- Pixel font (**Press Start 2P**, with monospace fallback) for all UI text.
- **Death = pixel explosion**: particle burst + screen shake.

## 3. AI Opponent — 4 selectable difficulties

Chosen on the title screen each match. Tron-flavored names:

| Level | Name | Brain |
|---|---|---|
| 1 | **USER** | Avoids immediate death, but short-sighted; makes random mistakes |
| 2 | **PROGRAM** | Always survives 1 tick, picks the move with the most open space (flood-fill count) |
| 3 | **SARK** | Territory-aware: Voronoi heuristic (claims more cells than you) + space counting, tries to cut you off |
| 4 | **MASTER CONTROL** | All of the above + a shallow minimax search (3 plies) — punishes greedy moves |

AI techniques per level (all pure JS, run once per tick):
- **Legality check** — never picks an instantly-fatal move if one exists (level ≥ 2).
- **Flood fill** — counts reachable free cells for each candidate direction.
- **Voronoi territory** — labels each free cell by who reaches it first; maximize own territory.
- **Minimax (level 4 only)** — 3-ply lookahead scoring positions with the above heuristics.

## 4. Power-Ups (the fun chaos layer)

Spawn randomly on free cells, one at a time, blinking pixel icons:

- ⚡ **TURBO** — double speed for ~2 seconds (your trail still kills).
- 🧽 **ERASER** — instantly deletes your entire trail (fresh escape route).

Whoever drives over it gets the effect. AI (level ≥ 2) factors pickups into its
scoring, so it will race you to them.

## 5. Sound (synthesized, zero assets)

WebAudio oscillators only: engine hum, turn blips, pickup chime, crash noise,
countdown beeps, victory/defeat jingles. Mute toggle in the HUD (`M`).

## 6. Screens & Flow

```
TITLE (logo, difficulty select, controls hint)
  └─> COUNTDOWN → ROUND → (crash) → ROUND RESULT → next round…
        └─ first to 5 → MATCH RESULT (rematch / change difficulty / title)
Pause with Esc or P.
```

## 7. Tech Architecture

```
vite + react (JavaScript, no TS — keeps it lean)
├── index.html            # font link, root div
├── vite.config.js        # host 0.0.0.0, allowedHosts for live preview
├── src/
│   ├── main.jsx / App.jsx        # screen state machine (menu ⇄ match)
│   ├── components/
│   │   ├── Menu.jsx              # title + difficulty picker
│   │   ├── Game.jsx              # canvas host, RAF loop, keyboard input
│   │   ├── HUD.jsx               # round pips, difficulty label, mute
│   │   └── Overlays.jsx          # countdown, round result, match result, pause
│   ├── game/
│   │   ├── engine.js             # grid, tick, collisions, power-ups, match state
│   │   ├── ai.js                 # difficulty brains: flood fill / voronoi / minimax
│   │   └── audio.js              # WebAudio retro SFX
│   └── render/
│       └── renderer.js           # pixel-art drawing, particles, screen shake
```

- Game logic is **plain JS modules** — React only renders UI chrome and screens,
  never per-tick state. Fixed-timestep game loop inside `requestAnimationFrame`.
- Keyboard input with a small input buffer so fast double-turns aren't dropped.

## 8. Scope guardrails (v1)

- Keyboard only (no touch controls) — can add later.
- 2 players: 1 human + 1 AI (no local 2P mode) — easy to add later.
- No persistence/high-scores (session score only).

---

**Plan: build it exactly as above, run it as a live preview, commit to the branch.**
