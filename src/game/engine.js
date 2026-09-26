// GRID DUEL — game engine
// Pure JS, no DOM dependencies. Can run in the browser or headless in Node.
//
// Grid semantics: Uint8Array of GRID_W * GRID_H cells.
//   0 = free, 1 = player 1 trail/head, 2 = player 2 trail/head.
// Heads are always marked in the grid (they become trail as the cycle moves).

export const GRID_W = 64
export const GRID_H = 48
export const CELL = 10 // px per cell at internal resolution
export const WIN_SCORE = 5 // rounds needed to win the match
export const TICK_MS = 60 // fixed timestep per game tick
export const TURBO_TICKS = 32 // ~2s of double speed
export const POWERUP_TTL = 220 // ticks before a powerup despawns
export const POWERUP_COOLDOWN = 60 // ticks after despawn/pickup before a new spawn roll

export const DIRS = {
  up: { x: 0, y: -1 },
  right: { x: 1, y: 0 },
  down: { x: 0, y: 1 },
  left: { x: -1, y: 0 },
}
export const DIR_LIST = [DIRS.up, DIRS.right, DIRS.down, DIRS.left]

export function idx(x, y) {
  return y * GRID_W + x
}

export function inBounds(x, y) {
  return x >= 0 && x < GRID_W && y >= 0 && y < GRID_H
}

export function isReverse(a, b) {
  return a.x === -b.x && a.y === -b.y
}

export function createPlayers(roundIndex) {
  // Alternate starting sides each round for fairness.
  const p1StartsLeft = roundIndex % 2 === 0
  const y = GRID_H >> 1
  return [
    {
      id: 1,
      x: p1StartsLeft ? 12 : GRID_W - 13,
      y,
      dir: p1StartsLeft ? DIRS.right : DIRS.left,
      alive: true,
      turboUntil: -1,
      inputQueue: [],
      deathCell: null,
    },
    {
      id: 2,
      x: p1StartsLeft ? GRID_W - 13 : 12,
      y,
      dir: p1StartsLeft ? DIRS.left : DIRS.right,
      alive: true,
      turboUntil: -1,
      inputQueue: [],
      deathCell: null,
    },
  ]
}

export function createRound(scores, roundIndex) {
  const players = createPlayers(roundIndex)
  const grid = new Uint8Array(GRID_W * GRID_H)
  for (const p of players) grid[idx(p.x, p.y)] = p.id
  return {
    phase: 'countdown', // 'countdown' | 'running' | 'roundover' | 'done'
    tick: 0,
    grid,
    players,
    scores: scores ? scores.slice() : [0, 0],
    roundIndex,
    powerup: null,
    powerupCooldown: 50,
    winner: null, // 0 | 1 | 'draw' (player index)
  }
}

export function cloneState(s) {
  return {
    phase: s.phase,
    tick: s.tick,
    grid: s.grid.slice(),
    players: s.players.map((p) => ({
      id: p.id,
      x: p.x,
      y: p.y,
      dir: p.dir,
      alive: p.alive,
      turboUntil: p.turboUntil,
      inputQueue: [],
      deathCell: p.deathCell,
    })),
    scores: s.scores,
    roundIndex: s.roundIndex,
    powerup: s.powerup ? { ...s.powerup } : null,
    powerupCooldown: s.powerupCooldown,
    winner: s.winner,
  }
}

// Buffer up to 2 direction changes so fast double-turns aren't dropped.
export function queueDir(p, dir) {
  if (!dir) return
  if (p.inputQueue.length >= 2) p.inputQueue.shift()
  p.inputQueue.push(dir)
}

function stepsFor(p, tick) {
  return tick < p.turboUntil ? 2 : 1
}

// Apply one simultaneous tick using each player's current `dir`.
// Mutates state (grid, player positions/alive, powerup pickup).
// Returns an events array: {type:'turn'|'crash'|'pickup'|'roundend', ...}
export function advance(state) {
  const events = []
  const { grid, players, tick } = state

  // Plan each player's path for this tick (1 cell, or 2 with turbo).
  const paths = players.map((p) => {
    const steps = stepsFor(p, tick)
    const cells = []
    let x = p.x
    let y = p.y
    for (let i = 0; i < steps; i++) {
      x += p.dir.x
      y += p.dir.y
      cells.push({ x, y })
    }
    return { p, cells, claims: new Set(), aliveCells: [], dies: false, deathCell: null }
  })

  // Resolve movement in synchronized sub-steps so head-on collisions
  // kill both riders deterministically.
  const maxSteps = Math.max(paths[0].cells.length, paths[1].cells.length)
  for (let k = 0; k < maxSteps; k++) {
    const c0 = paths[0].dies || k >= paths[0].cells.length ? null : paths[0].cells[k]
    const c1 = paths[1].dies || k >= paths[1].cells.length ? null : paths[1].cells[k]
    if (c0 && c1 && c0.x === c1.x && c0.y === c1.y) {
      paths[0].dies = true
      paths[0].deathCell = c0
      paths[1].dies = true
      paths[1].deathCell = c1
      break
    }
    for (let i = 0; i < 2; i++) {
      const c = i === 0 ? c0 : c1
      if (!c) continue
      const path = paths[i]
      const other = paths[1 - i]
      const key = c.y * GRID_W + c.x
      if (
        !inBounds(c.x, c.y) ||
        grid[idx(c.x, c.y)] !== 0 ||
        other.claims.has(key)
      ) {
        path.dies = true
        path.deathCell = c
      } else {
        path.claims.add(key)
        path.aliveCells.push(c)
      }
    }
  }

  // Commit: wall the traveled cells, move heads, record deaths.
  for (const path of paths) {
    const p = path.p
    for (const c of path.aliveCells) {
      grid[idx(c.x, c.y)] = p.id
      p.x = c.x
      p.y = c.y
    }
    if (path.dies) {
      p.alive = false
      p.deathCell = path.deathCell
      events.push({ type: 'crash', player: p.id, cell: path.deathCell })
    }
  }

  // Powerup pickup (only meaningful if the round continues).
  const pu = state.powerup
  if (players[0].alive && players[1].alive && pu) {
    for (let i = 0; i < 2; i++) {
      const p = players[i]
      if (p.x === pu.x && p.y === pu.y) {
        applyPowerup(state, p, events)
        break
      }
    }
  }

  return events
}

function applyPowerup(state, p, events) {
  const pu = state.powerup
  state.powerup = null
  state.powerupCooldown = POWERUP_COOLDOWN
  if (pu.type === 'turbo') {
    p.turboUntil = state.tick + TURBO_TICKS
    events.push({ type: 'pickup', player: p.id, powerup: 'turbo' })
  } else {
    // Eraser: wipe your own trail, keep the head cell.
    const { grid } = state
    const headIdx = idx(p.x, p.y)
    for (let i = 0; i < grid.length; i++) {
      if (grid[i] === p.id && i !== headIdx) grid[i] = 0
    }
    events.push({ type: 'pickup', player: p.id, powerup: 'eraser' })
  }
}

// Full game tick: consume buffered input, advance, resolve round outcome,
// handle powerup ttl/spawn.
export function stepTick(state) {
  const events = []
  for (const p of state.players) {
    while (p.inputQueue.length > 0) {
      const d = p.inputQueue.shift()
      if (d.x === p.dir.x && d.y === p.dir.y) continue // no-op, drop
      if (isReverse(d, p.dir)) continue // can't reverse, drop
      p.dir = d
      events.push({ type: 'turn', player: p.id })
      break
    }
  }

  events.push(...advance(state))

  const [p1, p2] = state.players
  if (!p1.alive || !p2.alive) {
    state.phase = 'roundover'
    if (!p1.alive && !p2.alive) state.winner = 'draw'
    else state.winner = p1.alive ? 0 : 1
    if (state.winner !== 'draw') state.scores[state.winner]++
    events.push({ type: 'roundend', winner: state.winner })
    return events
  }

  if (state.powerup) {
    if (--state.powerup.ttl <= 0) {
      state.powerup = null
      state.powerupCooldown = POWERUP_COOLDOWN
    }
  } else if (state.powerupCooldown > 0) {
    state.powerupCooldown--
  } else if (Math.random() < 0.035) {
    state.powerup = spawnPowerup(state)
  }

  state.tick++
  return events
}

function spawnPowerup(state) {
  for (let tries = 0; tries < 60; tries++) {
    const x = 2 + ((Math.random() * (GRID_W - 4)) | 0)
    const y = 2 + ((Math.random() * (GRID_H - 4)) | 0)
    if (state.grid[idx(x, y)] !== 0) continue
    let ok = true
    for (const p of state.players) {
      if (Math.abs(p.x - x) + Math.abs(p.y - y) < 6) {
        ok = false
        break
      }
    }
    if (!ok) continue
    return { x, y, type: Math.random() < 0.5 ? 'turbo' : 'eraser', ttl: POWERUP_TTL }
  }
  return null
}
