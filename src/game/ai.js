// GRID DUEL — AI opponent
// Pure JS, no DOM deps. Four brains, one per difficulty:
//   user    (lvl 1): short-sighted, noisy, makes mistakes
//   program (lvl 2): maximizes reachable space
//   sark    (lvl 3): 1-tick lookahead, Voronoi territory + powerup hunger
//   mcp     (lvl 4): alpha-beta minimax over joint moves, Voronoi eval
//
// The AI never cheats: it reads the same grid the human sees.

import {
  GRID_W,
  GRID_H,
  idx,
  inBounds,
  isReverse,
  DIR_LIST,
  advance,
  cloneState,
} from './engine.js'

export const DIFFICULTIES = [
  { key: 'user', name: 'USER', blurb: 'IT JUST LEARNED TO RIDE' },
  { key: 'program', name: 'PROGRAM', blurb: 'HOLDS ITS LINE, PLAYS IT SAFE' },
  { key: 'sark', name: 'SARK', blurb: 'HUNGERS FOR TERRITORY' },
  { key: 'mcp', name: 'MASTER CONTROL', blurb: 'IT SEES THREE MOVES AHEAD' },
]

const N = GRID_W * GRID_H

// ---------------------------------------------------------------------------
// Scratch buffers (module-level to avoid per-call allocation). Safe because
// chooseDir is never called re-entrantly.
// ---------------------------------------------------------------------------
const seen = new Int32Array(N) // stamp-based visited markers
const seenA = new Int32Array(N)
const seenB = new Int32Array(N)
const distA = new Int32Array(N)
const distB = new Int32Array(N)
const queue = new Int32Array(N + 8)
let stamp = 0
let vstamp = 0

function freeCell(grid, x, y) {
  return inBounds(x, y) && grid[idx(x, y)] === 0
}

function candidateDirs(p) {
  return DIR_LIST.filter((d) => !isReverse(d, p.dir))
}

// Count free cells reachable from (x,y), capped at `cap`.
// ((x,y) itself may be occupied — e.g. a head — it is only used as the seed.)
export function floodSpace(grid, x, y, cap) {
  stamp++
  let qh = 0
  let qt = 0
  let count = 0
  const start = idx(x, y)
  seen[start] = stamp
  queue[qt++] = start
  while (qh < qt && count < cap) {
    const cur = queue[qh++]
    const cx = cur % GRID_W
    const cy = (cur / GRID_W) | 0
    if (cy > 0) visit(cur - GRID_W)
    if (cx < GRID_W - 1) visit(cur + 1)
    if (cy < GRID_H - 1) visit(cur + GRID_W)
    if (cx > 0) visit(cur - 1)
  }
  return count

  function visit(ni) {
    if (seen[ni] === stamp || grid[ni] !== 0) return
    seen[ni] = stamp
    count++
    if (count < cap) queue[qt++] = ni
  }
}

// BFS distances from a seed over free cells.
function bfsFrom(grid, x, y, mark, dist, stampVal) {
  let qh = 0
  let qt = 0
  const h = idx(x, y)
  mark[h] = stampVal
  dist[h] = 0
  queue[qt++] = h
  while (qh < qt) {
    const cur = queue[qh++]
    const cx = cur % GRID_W
    const cy = (cur / GRID_W) | 0
    const nd = dist[cur] + 1
    if (cy > 0) expand(cur - GRID_W, nd)
    if (cx < GRID_W - 1) expand(cur + 1, nd)
    if (cy < GRID_H - 1) expand(cur + GRID_W, nd)
    if (cx > 0) expand(cur - 1, nd)
  }

  function expand(ni, nd) {
    if (mark[ni] === stampVal || grid[ni] !== 0) return
    mark[ni] = stampVal
    dist[ni] = nd
    queue[qt++] = ni
  }
}

// Voronoi analysis in one call:
//   territory[a] = free cells strictly closer to a
//   reach[a]     = all free cells a can reach at all
// Returns { aTerr, bTerr, aReach, bReach }.
function voronoi(grid, a, b) {
  vstamp += 2
  bfsFrom(grid, a.x, a.y, seenA, distA, vstamp)
  bfsFrom(grid, b.x, b.y, seenB, distB, vstamp + 1)
  let aTerr = 0
  let bTerr = 0
  let aReach = 0
  let bReach = 0
  for (let i = 0; i < N; i++) {
    if (grid[i] !== 0) continue
    const sa = seenA[i] === vstamp
    const sb = seenB[i] === vstamp + 1
    if (sa) aReach++
    if (sb) bReach++
    if (sa && sb) {
      if (distA[i] < distB[i]) aTerr++
      else if (distB[i] < distA[i]) bTerr++
    } else if (sa) aTerr++
    else if (sb) bTerr++
  }
  return { aTerr, bTerr, aReach, bReach }
}

// Static evaluation of a (simulated) state from the AI's perspective.
// Higher = better for the AI.
function evaluate(state, aiIndex) {
  const ai = state.players[aiIndex]
  const hu = state.players[1 - aiIndex]
  if (!ai.alive && !hu.alive) return -40000 // mutual destruction ~ bad trade
  if (!ai.alive) return -100000
  if (!hu.alive) return 200000

  const v = voronoi(state.grid, ai, hu)
  let s = (v.aTerr - v.bTerr) * 10

  if (v.aReach < 14) s += (v.aReach - 14) * 60 // panic when cramped
  if (v.bReach < 10) s += (10 - v.bReach) * 25 // enjoy the enemy's coffin

  if (state.powerup) {
    const pu = state.powerup
    const dai = Math.abs(ai.x - pu.x) + Math.abs(ai.y - pu.y)
    const dhu = Math.abs(hu.x - pu.x) + Math.abs(hu.y - pu.y)
    if (dai === 0) s += 60
    else if (dai < dhu) s += 14 - Math.min(dai, 12)
    else s -= 6
  }
  if (state.tick < ai.turboUntil) s += 25
  if (state.tick < hu.turboUntil) s -= 25
  return s
}

function terminalScore(aiAlive, huAlive) {
  if (aiAlive && !huAlive) return 200000
  if (!aiAlive && huAlive) return -100000
  return -40000
}

// Simulate one joint tick with the given dirs; returns the cloned, advanced
// state (never mutates the input).
function simTick(state, aiIndex, ad, hd) {
  const s = cloneState(state)
  s.players[aiIndex].dir = ad
  s.players[1 - aiIndex].dir = hd
  advance(s)
  return s
}

// Alpha-beta minimax over joint (AI, human) moves. Structured as
// max over AI dirs of (min over human dirs of value), recursing on joint
// outcomes. `depth` counts joint ticks of lookahead.
function search(state, aiIndex, depth, alpha, beta) {
  const ai = state.players[aiIndex]
  const hu = state.players[1 - aiIndex]
  let best = -Infinity
  for (const ad of candidateDirs(ai)) {
    let worst = Infinity
    for (const hd of candidateDirs(hu)) {
      const s = simTick(state, aiIndex, ad, hd)
      let v
      if (!s.players[aiIndex].alive || !s.players[1 - aiIndex].alive) {
        v = terminalScore(s.players[aiIndex].alive, s.players[1 - aiIndex].alive)
      } else if (depth <= 1) {
        v = evaluate(s, aiIndex)
      } else {
        v = search(s, aiIndex, depth - 1, alpha, worst)
      }
      if (v < worst) worst = v
      if (worst <= alpha || worst <= -99000) break // this AI move is refuted
    }
    if (worst > best) best = worst
    if (best > alpha) alpha = best
    if (alpha >= beta) break
  }
  return best
}

// MASTER CONTROL. Full-width search only when it matters (riders close or
// space tight); otherwise 2 ticks of lookahead are plenty.
function chooseDirMCP(state, aiIndex) {
  const ai = state.players[aiIndex]
  const hu = state.players[1 - aiIndex]
  const legal = candidateDirs(ai).filter((d) =>
    freeCell(state.grid, ai.x + d.x, ai.y + d.y)
  )
  if (legal.length === 0) return ai.dir
  if (legal.length === 1) return legal[0]

  const near =
    Math.abs(ai.x - hu.x) + Math.abs(ai.y - hu.y) < 14 ||
    floodSpace(state.grid, ai.x, ai.y, 130) < 70
  const depth = near ? 3 : 2

  let bestDir = null
  let bestVal = -Infinity
  let alpha = -Infinity
  for (const ad of legal) {
    let worst = Infinity
    for (const hd of candidateDirs(hu)) {
      const s = simTick(state, aiIndex, ad, hd)
      let v
      if (!s.players[aiIndex].alive || !s.players[1 - aiIndex].alive) {
        v = terminalScore(s.players[aiIndex].alive, s.players[1 - aiIndex].alive)
      } else if (depth <= 1) {
        v = evaluate(s, aiIndex)
      } else {
        v = search(s, aiIndex, depth - 1, alpha, worst)
      }
      if (v < worst) worst = v
      if (worst <= alpha || worst <= -99000) break
    }
    if (worst > bestVal) {
      bestVal = worst
      bestDir = ad
    }
    if (bestVal > alpha) alpha = bestVal
  }
  return bestDir || ai.dir
}

// Sark: 1-tick lookahead, assumes the human keeps their current heading.
function chooseDirSark(state, aiIndex) {
  const ai = state.players[aiIndex]
  const hu = state.players[1 - aiIndex]
  let bestDir = null
  let bestVal = -Infinity
  for (const ad of candidateDirs(ai)) {
    const s = simTick(state, aiIndex, ad, hu.dir)
    let v
    if (!s.players[aiIndex].alive) v = -100000
    else if (!s.players[1 - aiIndex].alive) v = 200000
    else v = evaluate(s, aiIndex)
    v += Math.random() * 3 // tiny jitter to avoid deterministic loops
    if (v > bestVal) {
      bestVal = v
      bestDir = ad
    }
  }
  return bestDir || ai.dir
}

// Program: greedily maximize reachable space.
function chooseDirProgram(state, aiIndex) {
  const ai = state.players[aiIndex]
  const { grid } = state
  let bestDir = null
  let bestVal = -Infinity
  for (const d of candidateDirs(ai)) {
    if (!freeCell(grid, ai.x + d.x, ai.y + d.y)) continue
    let v = floodSpace(grid, ai.x + d.x, ai.y + d.y, 500) * 10
    if (d === ai.dir) v += 4 // mild preference for holding the line
    v += Math.random() * 2
    if (v > bestVal) {
      bestVal = v
      bestDir = d
    }
  }
  return bestDir || ai.dir
}

// User: short-sighted and noisy. Avoids instant death but little else.
function chooseDirUser(state, aiIndex) {
  const ai = state.players[aiIndex]
  const { grid } = state
  const legal = candidateDirs(ai).filter((d) =>
    freeCell(grid, ai.x + d.x, ai.y + d.y)
  )
  if (legal.length === 0) return ai.dir
  if (Math.random() < 0.22) return legal[(Math.random() * legal.length) | 0]
  let bestDir = null
  let bestVal = -Infinity
  for (const d of legal) {
    const v =
      Math.min(floodSpace(grid, ai.x + d.x, ai.y + d.y, 16), 16) * 10 +
      Math.random() * 70
    if (v > bestVal) {
      bestVal = v
      bestDir = d
    }
  }
  return bestDir
}

// Public API. `difficultyKey` is one of DIFFICULTIES[].key.
export function chooseDir(state, aiIndex, difficultyKey) {
  switch (difficultyKey) {
    case 'user':
      return chooseDirUser(state, aiIndex)
    case 'program':
      return chooseDirProgram(state, aiIndex)
    case 'sark':
      return chooseDirSark(state, aiIndex)
    case 'mcp':
      return chooseDirMCP(state, aiIndex)
    default:
      return chooseDirProgram(state, aiIndex)
  }
}

// Exported for tests/sim.
export const _internals = { floodSpace, voronoi, evaluate }
