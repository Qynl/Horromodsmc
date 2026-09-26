// GRID DUEL — pixel-art canvas renderer.
// Internal resolution is GRID_W*CELL x GRID_H*CELL (640x480), upscaled by CSS
// with image-rendering: pixelated. CRT scanlines/vignette live in CSS.

import { GRID_W, GRID_H, CELL, idx } from '../game/engine.js'

const W = GRID_W * CELL
const H = GRID_H * CELL

const PLAYER_COLORS = [
  null,
  { body: '#0090a8', core: '#00e5ff', head: '#c9fbff', spark: '#7ff7ff' },
  { body: '#b35614', core: '#ff9633', head: '#ffe8c9', spark: '#ffc48a' },
]

const TURBO_COLOR = '#ffe14d'
const ERASER_COLOR = '#ff4dd2'

// 8x8 pixel sprites ('.' = transparent, '1'/'2' = palette colors)
const SPRITE_TURBO = [
  '....111.',
  '...111..',
  '..111...',
  '.111111.',
  '...111..',
  '..111...',
  '.111....',
  '.11.....',
]
const SPRITE_ERASER = [
  '........',
  '..1111..',
  '.111111.',
  '.111111.',
  '.222222.',
  '.222222.',
  '..2222..',
  '........',
]

function drawSprite(ctx, rows, x, y, palette) {
  for (let r = 0; r < rows.length; r++) {
    const row = rows[r]
    for (let c = 0; c < row.length; c++) {
      const ch = row[c]
      if (ch === '.') continue
      ctx.fillStyle = palette[ch]
      ctx.fillRect(x + c, y + r, 1, 1)
    }
  }
}

export function createRenderer(canvas) {
  canvas.width = W
  canvas.height = H
  const ctx = canvas.getContext('2d')
  ctx.imageSmoothingEnabled = false

  // ---- static background pre-render (bg fill, grid lines, frame, vignette)
  const bg = document.createElement('canvas')
  bg.width = W
  bg.height = H
  {
    const b = bg.getContext('2d')
    b.fillStyle = '#04040d'
    b.fillRect(0, 0, W, H)

    // faint grid lines
    b.fillStyle = 'rgba(0, 229, 255, 0.05)'
    for (let x = 1; x < GRID_W; x++) b.fillRect(x * CELL, 0, 1, H)
    for (let y = 1; y < GRID_H; y++) b.fillRect(0, y * CELL, W, 1)

    // danger frame
    b.strokeStyle = '#1c2f57'
    b.lineWidth = 2
    b.strokeRect(1, 1, W - 2, H - 2)

    // soft vignette baked in
    const g = b.createRadialGradient(W / 2, H / 2, H * 0.35, W / 2, H / 2, H * 0.85)
    g.addColorStop(0, 'rgba(0,0,0,0)')
    g.addColorStop(1, 'rgba(0,0,0,0.35)')
    b.fillStyle = g
    b.fillRect(0, 0, W, H)
  }

  let particles = []
  let shake = 0

  function explode(cell, playerId) {
    if (!cell) return
    const colors = PLAYER_COLORS[playerId]
    const main = colors ? colors.spark : '#ffffff'
    const cx = cell.x * CELL + CELL / 2
    const cy = cell.y * CELL + CELL / 2
    for (let i = 0; i < 52; i++) {
      const ang = Math.random() * Math.PI * 2
      const sp = 0.5 + Math.random() * 3.4
      particles.push({
        x: cx,
        y: cy,
        vx: Math.cos(ang) * sp,
        vy: Math.sin(ang) * sp,
        life: 1,
        decay: 0.01 + Math.random() * 0.03,
        size: Math.random() < 0.3 ? 4 : 2,
        color: Math.random() < 0.35 ? '#ffffff' : main,
      })
    }
    shake = 9
  }

  function reset() {
    particles = []
    shake = 0
  }

  function drawFrame(state, now) {
    ctx.save()
    if (shake > 0.4) {
      ctx.translate(((Math.random() - 0.5) * shake) | 0, ((Math.random() - 0.5) * shake) | 0)
      shake *= 0.87
    } else {
      shake = 0
    }

    ctx.drawImage(bg, 0, 0)

    const { grid, players, tick } = state

    // trails — batch by player to minimize fillStyle churn
    for (let pi = 1; pi <= 2; pi++) {
      const c = PLAYER_COLORS[pi]
      // body blocks (1px gaps => chunky pixel-wall look)
      ctx.fillStyle = c.body
      for (let i = 0; i < grid.length; i++) {
        if (grid[i] !== pi) continue
        const x = (i % GRID_W) * CELL
        const y = ((i / GRID_W) | 0) * CELL
        ctx.fillRect(x + 1, y + 1, CELL - 2, CELL - 2)
      }
      // glowing inner core
      ctx.fillStyle = c.core
      for (let i = 0; i < grid.length; i++) {
        if (grid[i] !== pi) continue
        const x = (i % GRID_W) * CELL
        const y = ((i / GRID_W) | 0) * CELL
        ctx.fillRect(x + 3, y + 3, CELL - 6, CELL - 6)
      }
    }

    // powerup
    if (state.powerup) {
      const pu = state.powerup
      const px = pu.x * CELL
      const py = pu.y * CELL
      const blinkOn = ((now / 150) | 0) % 5 !== 4
      const color = pu.type === 'turbo' ? TURBO_COLOR : ERASER_COLOR
      // pulsing diamond marker
      ctx.save()
      ctx.translate(px + CELL / 2, py + CELL / 2)
      ctx.rotate(Math.PI / 4)
      const pulse = 4 + Math.sin(now / 180) * 1.5
      ctx.strokeStyle = color
      ctx.lineWidth = 1
      ctx.globalAlpha = 0.75
      ctx.strokeRect(-pulse, -pulse, pulse * 2, pulse * 2)
      ctx.restore()
      ctx.globalAlpha = 1
      if (blinkOn) {
        if (pu.type === 'turbo') {
          drawSprite(ctx, SPRITE_TURBO, px + 1, py + 1, { 1: TURBO_COLOR })
        } else {
          drawSprite(
            ctx,
            SPRITE_ERASER,
            px + 1,
            py + 1,
            { 1: '#f4f4ff', 2: ERASER_COLOR }
          )
        }
      }
    }

    // cycle heads
    for (const p of players) {
      if (!p.alive) continue
      const c = PLAYER_COLORS[p.id]
      const x = p.x * CELL
      const y = p.y * CELL
      const turbo = tick < p.turboUntil
      if (turbo) {
        // flickering aura + sparks
        if (((now / 60) | 0) % 2 === 0) {
          ctx.strokeStyle = TURBO_COLOR
          ctx.lineWidth = 1
          ctx.strokeRect(x - 1.5, y - 1.5, CELL + 3, CELL + 3)
        }
        ctx.fillStyle = TURBO_COLOR
        for (let s = 0; s < 3; s++) {
          ctx.fillRect(
            x + ((Math.random() * (CELL + 8)) | 0) - 4,
            y + ((Math.random() * (CELL + 8)) | 0) - 4,
            2,
            2
          )
        }
      }
      ctx.fillStyle = turbo ? TURBO_COLOR : c.head
      ctx.fillRect(x + 1, y + 1, CELL - 2, CELL - 2)
      ctx.fillStyle = '#ffffff'
      ctx.fillRect(x + 3, y + 3, CELL - 6, CELL - 6)
    }

    // particles
    for (let i = particles.length - 1; i >= 0; i--) {
      const pt = particles[i]
      pt.x += pt.vx
      pt.y += pt.vy
      pt.vx *= 0.96
      pt.vy *= 0.96
      pt.life -= pt.decay
      if (pt.life <= 0) {
        particles.splice(i, 1)
        continue
      }
      ctx.globalAlpha = pt.life > 1 ? 1 : pt.life
      ctx.fillStyle = pt.color
      ctx.fillRect(pt.x | 0, pt.y | 0, pt.size, pt.size)
    }
    ctx.globalAlpha = 1
    ctx.restore()
  }

  return { drawFrame, explode, reset }
}
