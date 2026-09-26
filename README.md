# GRID DUEL

A retro **light-cycle deathmatch** — you vs. an AI, Tron-style, wrapped in an
80s arcade cabinet. Chunky pixels, CRT scanlines, synthesized bleeps, and an
opponent that genuinely wants to wall you in.

![genre](https://img.shields.io/badge/genre-arcade%202D%20vs%20AI-criticality) ![stack](https://img.shields.io/badge/stack-React%20%2B%20Vite%20%2B%20Canvas-informational)

## Play

```bash
npm install
npm run dev        # then open the printed URL
```

**Controls**

| Key | Action |
|---|---|
| Arrow keys / WASD | steer your cycle |
| P or Esc | pause |
| M | sound on/off |
| Enter | confirm menus / rematch |

Both riders leave a solid wall of light. Hit any wall — yours, theirs, or the
arena border — and you derezz. **First to 5 round wins takes the match.**

**Power-ups** (blink on the grid, race the AI to them)

- ⚡ **TURBO** — double speed for ~2 seconds
- ⌗ **ERASER** — instantly deletes your entire trail

## The AI

Four selectable opponents, all playing with the same information you can see
(no cheating):

| Opponent | Brain |
|---|---|
| **USER** | short-sighted, noisy, makes mistakes |
| **PROGRAM** | greedily maximizes its reachable space (flood fill) |
| **SARK** | 1-tick lookahead with a Voronoi territory heuristic + powerup hunger |
| **MASTER CONTROL** | alpha-beta minimax over joint moves (up to 3 ticks ahead), territory evaluation |

## Architecture

```
src/
├── game/
│   ├── engine.js     # pure game logic: grid, movement, collisions, powerups
│   ├── ai.js         # the four brains: flood fill / voronoi / minimax
│   └── audio.js      # WebAudio synth: all SFX, zero audio assets
├── render/
│   └── renderer.js   # pixel-art canvas renderer (particles, shake, sprites)
├── components/       # React UI shell: menu, HUD, overlays, game host
└── App.jsx / styles.css / main.jsx
```

Game logic is pure JS with no DOM dependencies — React only renders UI chrome,
the fixed-timestep loop (60 ms/tick) lives in a `requestAnimationFrame` loop.

## Testing

The engine, AI, renderer and full UI flow are all testable headless:

```bash
npm run sim                 # AI ladder + random-bot sanity + engine invariants
node scripts/sim.js perf    # MASTER CONTROL decision timing
node scripts/visual-preview.mjs   # renders real frames, pixel-level assertions
npx vite build --config vite.dom-test.config.js
node scripts/dom-smoke.mjs        # full menu→match→pause→quit flow in jsdom
node scripts/dom-smoke-match.mjs  # full 5-round match → defeat → rematch flow
```

The headless tests run the *real* renderer through a tiny software Canvas 2D
implementation (`scripts/software-canvas.mjs`), so drawing code is verified
without a browser.
