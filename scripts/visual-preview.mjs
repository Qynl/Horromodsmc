// Headless visual QA: run the REAL renderer (src/render/renderer.js) against
// real engine states using the software canvas, then verify pixels
// programmatically + print ASCII previews (agent has no eyes).
import fs from 'node:fs'
import { PNG } from 'pngjs'
import { SoftwareCanvas } from './software-canvas.mjs'

globalThis.document = {
  createElement: (tag) => {
    if (tag === 'canvas') return new SoftwareCanvas(1, 1)
    throw new Error('unexpected createElement: ' + tag)
  },
}

const { createRenderer } = await import('../src/render/renderer.js')
const { createRound, stepTick, idx, GRID_W, GRID_H, CELL } = await import(
  '../src/game/engine.js'
)
const { chooseDir } = await import('../src/game/ai.js')

let failures = 0
function check(name, cond, detail = '') {
  if (cond) console.log(`  ok  ${name}`)
  else {
    failures++
    console.log(`  FAIL ${name} ${detail}`)
  }
}

function savePNG(canvas, path) {
  const ctx = canvas.getContext('2d')
  const buf = ctx.buf
  const png = new PNG({ width: buf.width, height: buf.height })
  for (let i = 0; i < buf.width * buf.height; i++) {
    png.data[i * 4] = Math.round(buf.data[i * 4])
    png.data[i * 4 + 1] = Math.round(buf.data[i * 4 + 1])
    png.data[i * 4 + 2] = Math.round(buf.data[i * 4 + 2])
    png.data[i * 4 + 3] = 255
  }
  fs.mkdirSync('shots', { recursive: true })
  fs.writeFileSync(path, PNG.sync.write(png))
}

function classify(r, g, b) {
  if (r > 200 && g > 200 && b > 200) return 'W' // white head core
  if (r < 40 && g < 40 && b < 40) return '.' // background
  if (b > 150 && g > 100 && r < 100) return 'C' // cyan
  if (r > 180 && g > 90 && g < 200 && b < 100) return 'O' // orange
  if (r > 200 && g > 180 && b < 140) return 'Y' // yellow (turbo)
  if (r > 200 && b > 150 && g < 140) return 'M' // magenta
  if (r > 60 && g > 50 && b > 40 && b < 130 && g < 160) return 'o' // orange body
  if (b > 90 && g < 160 && r < 90) return 'c' // cyan body
  return '?'
}

function asciiPreview(canvas, title) {
  const ctx = canvas.getContext('2d')
  const buf = ctx.buf
  const cols = 96
  const rows = Math.round((buf.height / buf.width) * cols * 0.5)
  const lines = [title]
  for (let ry = 0; ry < rows; ry++) {
    let line = ''
    for (let rx = 0; rx < cols; rx++) {
      const px = Math.floor(((rx + 0.5) / cols) * buf.width)
      const py = Math.floor(((ry + 0.5) / rows) * buf.height)
      const i = (py * buf.width + px) * 4
      line += classify(buf.data[i], buf.data[i + 1], buf.data[i + 2])
    }
    lines.push(line)
  }
  console.log(lines.join('\n'))
}

function pixelAt(canvas, px, py) {
  const buf = canvas.getContext('2d').buf
  const i = (py * buf.width + px) * 4
  return [buf.data[i], buf.data[i + 1], buf.data[i + 2]]
}

// sanity: framebuffer has no NaN / wild values
function checkFinite(canvas, name) {
  const buf = canvas.getContext('2d').buf
  for (let i = 0; i < buf.data.length; i++) {
    if (!Number.isFinite(buf.data[i])) {
      check(name, false, `non-finite at ${i}`)
      return
    }
  }
  check(name, true)
}

const canvas = new SoftwareCanvas(1, 1)
const renderer = createRenderer(canvas)

// =========================================================================
console.log('\n== frame 1: round start ==')
let state = createRound([0, 0], 0)
renderer.reset()
renderer.drawFrame(state, 0)
savePNG(canvas, 'shots/a-start.png')
checkFinite(canvas, 'finite pixels')
{
  // heads at (12,24) and (51,24): center pixel should be white
  const [r1, g1, b1] = pixelAt(canvas, 12 * CELL + 5, 24 * CELL + 5)
  check('p1 head white-ish', r1 > 180 && g1 > 180 && b1 > 180, `got ${r1},${g1},${b1}`)
  const [r2, g2, b2] = pixelAt(canvas, 51 * CELL + 5, 24 * CELL + 5)
  check('p2 head white-ish', r2 > 180 && g2 > 180 && b2 > 180, `got ${r2},${g2},${b2}`)
  // empty cell is dark background
  const [r3, g3, b3] = pixelAt(canvas, 30 * CELL + 5, 10 * CELL + 5)
  check('empty cell dark', r3 < 40 && g3 < 40 && b3 < 40, `got ${r3},${g3},${b3}`)
}
asciiPreview(canvas, '--- a-start ---')

// =========================================================================
console.log('\n== frame 2: mid-game (sark vs sark) ==')
state = createRound([0, 0], 0)
state.phase = 'running'
for (let t = 0; t < 90 && state.phase === 'running'; t++) {
  state.players[0].dir = chooseDir(state, 0, 'sark')
  state.players[1].dir = chooseDir(state, 1, 'sark')
  stepTick(state)
}
renderer.reset()
renderer.drawFrame(state, 5000)
savePNG(canvas, 'shots/b-midgame.png')
checkFinite(canvas, 'finite pixels')
{
  // verify trail cells render in player colors: sample the cell behind each head
  const p1 = state.players[0]
  const p2 = state.players[1]
  const t1x = (p1.x - p1.dir.x) * CELL + 5
  const t1y = (p1.y - p1.dir.y) * CELL + 5
  const [r, g, b] = pixelAt(canvas, t1x, t1y)
  check('p1 trail cyan-ish', b > 120 && r < 100, `got ${r},${g},${b} at ${t1x},${t1y}`)
  const t2x = (p2.x - p2.dir.x) * CELL + 5
  const t2y = (p2.y - p2.dir.y) * CELL + 5
  const [r2, g2, b2] = pixelAt(canvas, t2x, t2y)
  check('p2 trail orange-ish', r2 > 120 && b2 < 110, `got ${r2},${g2},${b2} at ${t2x},${t2y}`)
  // count colored pixels to ensure substantial trails exist
  const buf = canvas.getContext('2d').buf
  let cyan = 0
  let orange = 0
  for (let i = 0; i < buf.width * buf.height; i++) {
    const c = classify(buf.data[i * 4], buf.data[i * 4 + 1], buf.data[i * 4 + 2])
    if (c === 'C' || c === 'c') cyan++
    if (c === 'O' || c === 'o') orange++
  }
  check('substantial cyan trail', cyan > 400, `cyan=${cyan}`)
  check('substantial orange trail', orange > 400, `orange=${orange}`)
}
asciiPreview(canvas, '--- b-midgame ---')

// =========================================================================
console.log('\n== frame 3: powerups + turbo ==')
state.powerup = { x: 20, y: 10, type: 'turbo', ttl: 100 }
renderer.reset()
renderer.drawFrame(state, 5200)
state.powerup = { x: 44, y: 38, type: 'eraser', ttl: 100 }
renderer.drawFrame(state, 5400)
state.powerup = null
state.players[0].turboUntil = state.tick + 20
renderer.drawFrame(state, 5600)
savePNG(canvas, 'shots/c-powerups-turbo.png')
checkFinite(canvas, 'finite pixels')
{
  // turbo head: yellow ring around the white core (sample the ring pixel)
  const p1 = state.players[0]
  const [r, g, b] = pixelAt(canvas, p1.x * CELL + 2, p1.y * CELL + 5)
  check('turbo head yellow ring', r > 200 && g > 180 && b < 160, `got ${r},${g},${b}`)
}
asciiPreview(canvas, '--- c-powerups-turbo (yellow head = turbo p1) ---')

// separate: powerup sprites check (re-render with each powerup, blink on)
state.players[0].turboUntil = -1
state.powerup = { x: 20, y: 10, type: 'turbo', ttl: 100 }
renderer.reset()
renderer.drawFrame(state, 0) // t=0 => blinkOn true
{
  // yellow pixels inside powerup cell
  let yellow = 0
  for (let py = 10 * CELL; py < 11 * CELL; py++)
    for (let px = 20 * CELL; px < 21 * CELL; px++) {
      const [r, g, b] = pixelAt(canvas, px, py)
      if (r > 200 && g > 180 && b < 140) yellow++
    }
  check('turbo bolt visible', yellow > 10, `yellow=${yellow}`)
}
state.powerup = { x: 44, y: 38, type: 'eraser', ttl: 100 }
renderer.reset()
renderer.drawFrame(state, 0)
{
  let magenta = 0
  let white = 0
  for (let py = 38 * CELL; py < 39 * CELL; py++)
    for (let px = 44 * CELL; px < 45 * CELL; px++) {
      const [r, g, b] = pixelAt(canvas, px, py)
      if (r > 200 && b > 150 && g < 140) magenta++
      if (r > 200 && g > 200 && b > 200) white++
    }
  check('eraser sprite visible', magenta > 5 && white > 5, `magenta=${magenta} white=${white}`)
}
savePNG(canvas, 'shots/c2-eraser.png')

// =========================================================================
console.log('\n== frame 4: crash + explosion ==')
state = createRound([2, 1], 3)
state.phase = 'running'
let guard = 0
while (state.phase === 'running' && guard++ < 3000) {
  state.players[0].dir = chooseDir(state, 0, 'user')
  state.players[1].dir = chooseDir(state, 1, 'user')
  stepTick(state)
}
renderer.reset()
const deadPlayer = state.players[0].alive ? state.players[1] : state.players[0]
renderer.explode(deadPlayer.deathCell, deadPlayer.id)
renderer.drawFrame(state, 8000)
savePNG(canvas, 'shots/d-crash.png')
checkFinite(canvas, 'finite pixels')
{
  const dc = deadPlayer.deathCell
  let bright = 0
  for (let py = dc.y * CELL - 20; py < dc.y * CELL + 30; py++)
    for (let px = dc.x * CELL - 20; px < dc.x * CELL + 30; px++) {
      if (px < 0 || py < 0) continue
      const [r, g, b] = pixelAt(canvas, px, py)
      if (r > 150 || g > 150 || b > 150) bright++
    }
  check('explosion particles near death cell', bright > 30, `bright=${bright}`)
  check('round actually ended', state.phase === 'roundover', state.phase)
}
asciiPreview(canvas, '--- d-crash ---')

// =========================================================================
console.log('\n== frame 5: late-game tangle (mcp vs sark) ==')
state = createRound([2, 2], 2)
state.phase = 'running'
guard = 0
while (state.phase === 'running' && guard++ < 1400) {
  state.players[0].dir = chooseDir(state, 0, 'mcp')
  state.players[1].dir = chooseDir(state, 1, 'sark')
  stepTick(state)
}
renderer.reset()
renderer.drawFrame(state, 20000)
savePNG(canvas, 'shots/e-lategame.png')
checkFinite(canvas, 'finite pixels')
asciiPreview(canvas, '--- e-lategame ---')
console.log(`round ended tick=${state.tick} winner=${state.winner}`)

console.log(failures === 0 ? '\nALL VISUAL CHECKS PASSED' : `\n${failures} CHECKS FAILED`)
process.exit(failures === 0 ? 0 : 1)
