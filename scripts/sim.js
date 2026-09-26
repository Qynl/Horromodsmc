// GRID DUEL — headless AI simulation.
// Runs engine + AI in Node (both are pure JS, no DOM deps).
//   node scripts/sim.js            — ladder + random-bot report
//   node scripts/sim.js perf       — MASTER CONTROL decision timing

import { createRound, stepTick, WIN_SCORE, idx } from '../src/game/engine.js'
import { chooseDir, DIFFICULTIES } from '../src/game/ai.js'

const MAX_TICKS = 20000
const MAX_ROUNDS = 25

function assertInvariants(state) {
  const { grid, players } = state
  for (let i = 0; i < grid.length; i++) {
    if (grid[i] !== 0 && grid[i] !== 1 && grid[i] !== 2) {
      throw new Error(`bad grid value ${grid[i]} at ${i}`)
    }
  }
  for (const p of players) {
    if (p.x < 0 || p.x >= 64 || p.y < 0 || p.y >= 48) {
      throw new Error(`player ${p.id} out of bounds (${p.x},${p.y})`)
    }
    if (p.alive && grid[idx(p.x, p.y)] !== p.id) {
      throw new Error(`player ${p.id} head not marked in grid`)
    }
  }
}

function randomDir(state, i) {
  const p = state.players[i]
  const opts = [
    { x: 0, y: -1 },
    { x: 1, y: 0 },
    { x: 0, y: 1 },
    { x: -1, y: 0 },
  ].filter(
    (d) =>
      !(d.x === -p.dir.x && d.y === -p.dir.y) &&
      p.x + d.x >= 0 &&
      p.x + d.x < 64 &&
      p.y + d.y >= 0 &&
      p.y + d.y < 48 &&
      state.grid[idx(p.x + d.x, p.y + d.y)] === 0
  )
  if (opts.length === 0) return p.dir
  return opts[(Math.random() * opts.length) | 0]
}

// Plays one match. dirFnA/dirFnB: (state, index) => dir
function playMatch(dirFnA, dirFnB) {
  let state = createRound([0, 0], 0)
  let draws = 0
  let guard = 0
  while (guard++ < MAX_TICKS) {
    if (state.phase === 'roundover') {
      if (state.winner === 'draw') draws++
      if (state.scores[0] >= WIN_SCORE || state.scores[1] >= WIN_SCORE) break
      if (state.roundIndex >= MAX_ROUNDS) break
      state = createRound(state.scores, state.roundIndex + 1)
      continue
    }
    if (state.phase === 'countdown') state.phase = 'running'
    state.players[0].dir = dirFnA(state, 0)
    state.players[1].dir = dirFnB(state, 1)
    stepTick(state)
    assertInvariants(state)
  }
  return { state, draws }
}

const aiDir = (key) => (state, i) => chooseDir(state, i, key)

const mode = process.argv[2] || 'ladder'

if (mode === 'perf') {
  let state = createRound([0, 0], 0)
  const times = []
  let guard = 0
  while (guard++ < MAX_TICKS) {
    if (state.phase === 'roundover') {
      if (state.scores[0] >= WIN_SCORE || state.scores[1] >= WIN_SCORE) break
      state = createRound(state.scores, state.roundIndex + 1)
      continue
    }
    if (state.phase === 'countdown') state.phase = 'running'
    const t0 = performance.now()
    state.players[1].dir = chooseDir(state, 1, 'mcp')
    times.push(performance.now() - t0)
    state.players[0].dir = chooseDir(state, 0, 'program')
    stepTick(state)
  }
  times.sort((a, b) => a - b)
  const avg = times.reduce((a, b) => a + b, 0) / times.length
  const p = (q) => times[Math.min(times.length - 1, Math.floor(q * times.length))]
  console.log(
    `MCP decisions: n=${times.length} avg=${avg.toFixed(2)}ms p50=${p(0.5).toFixed(2)}ms p95=${p(0.95).toFixed(2)}ms max=${times[times.length - 1].toFixed(2)}ms (tick budget: 60ms)`
  )
} else {
  const names = DIFFICULTIES.map((d) => d.name)
  const gamesFor = (a, b) => (a === 3 || b === 3 ? 3 : 5)

  console.log('AI LADDER (winsA-winsB, d=draws)\n')
  const results = []
  for (let a = 0; a < DIFFICULTIES.length; a++) {
    for (let b = a; b < DIFFICULTIES.length; b++) {
      const G = gamesFor(a, b)
      let winsA = 0
      let winsB = 0
      let draws = 0
      for (let g = 0; g < G; g++) {
        const r = playMatch(aiDir(DIFFICULTIES[a].key), aiDir(DIFFICULTIES[b].key))
        draws += r.draws
        if (r.state.scores[0] > r.state.scores[1]) winsA++
        else if (r.state.scores[1] > r.state.scores[0]) winsB++
      }
      results.push({ a, b, winsA, winsB, draws })
      console.log(
        `  ${names[a].padEnd(15)} vs ${names[b].padEnd(15)} ${winsA}-${winsB} (${draws}d)`
      )
    }
  }

  console.log('\nEACH LEVEL vs RANDOM BOT (3 matches):')
  for (let a = 0; a < DIFFICULTIES.length; a++) {
    let winsA = 0
    let winsB = 0
    for (let g = 0; g < 3; g++) {
      const r = playMatch(aiDir(DIFFICULTIES[a].key), randomDir)
      if (r.state.scores[0] > r.state.scores[1]) winsA++
      else if (r.state.scores[1] > r.state.scores[0]) winsB++
    }
    console.log(
      `  ${names[a].padEnd(15)} vs RANDOM: ${winsA}-${winsB} ${winsB > 0 ? '<-- WEAK!' : 'ok'}`
    )
  }
  console.log('\nAll engine invariants held. No exceptions.')
}
