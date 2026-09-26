// Minimal software Canvas 2D implementation for headless visual QA.
// Implements exactly the API subset used by src/render/renderer.js:
//   fillRect, strokeRect, clearRect, drawImage, save/restore,
//   translate/rotate, globalAlpha, fillStyle/strokeStyle/lineWidth,
//   createRadialGradient (rendered as no-op fill), imageSmoothingEnabled.
// Hard-edged rasterization (no AA) — the game is pixel-art anyway.

export class SoftwareCanvas {
  constructor(width = 1, height = 1) {
    this.width = width
    this.height = height
    this._ctx = null
  }
  getContext(type) {
    if (type !== '2d') return null
    if (!this._ctx) this._ctx = new SoftwareCtx(this)
    return this._ctx
  }
}

function parseColor(c) {
  if (typeof c === 'string') {
    c = c.trim()
    if (c.startsWith('#')) {
      const hex = c.slice(1)
      if (hex.length === 3) {
        return [
          parseInt(hex[0] + hex[0], 16),
          parseInt(hex[1] + hex[1], 16),
          parseInt(hex[2] + hex[2], 16),
          1,
        ]
      }
      if (hex.length === 6 || hex.length === 8) {
        return [
          parseInt(hex.slice(0, 2), 16),
          parseInt(hex.slice(2, 4), 16),
          parseInt(hex.slice(4, 6), 16),
          hex.length === 8 ? parseInt(hex.slice(6, 8), 16) / 255 : 1,
        ]
      }
    }
    const m = c.match(/^rgba?\(([^)]+)\)$/i)
    if (m) {
      const parts = m[1].split(',').map((s) => parseFloat(s.trim()))
      return [parts[0], parts[1], parts[2], parts.length > 3 ? parts[3] : 1]
    }
  }
  // gradients / unknown -> transparent no-op
  return [0, 0, 0, 0]
}

export class SoftwareCtx {
  constructor(canvas) {
    this.canvas = canvas
    this.imageSmoothingEnabled = true
    this.fillStyle = '#000000'
    this.strokeStyle = '#000000'
    this.lineWidth = 1
    this.globalAlpha = 1
    this._m = [1, 0, 0, 1, 0, 0] // a b c d e f
    this._stack = []
    this._buf = null
  }

  get buf() {
    if (!this._buf || this._buf.width !== this.canvas.width || this._buf.height !== this.canvas.height) {
      const { width: w, height: h } = this.canvas
      this._buf = { width: w, height: h, data: new Float64Array(w * h * 4) }
    }
    return this._buf
  }

  save() {
    this._stack.push({
      fillStyle: this.fillStyle,
      strokeStyle: this.strokeStyle,
      lineWidth: this.lineWidth,
      globalAlpha: this.globalAlpha,
      m: this._m.slice(),
    })
  }

  restore() {
    const s = this._stack.pop()
    if (!s) return
    this.fillStyle = s.fillStyle
    this.strokeStyle = s.strokeStyle
    this.lineWidth = s.lineWidth
    this.globalAlpha = s.globalAlpha
    this._m = s.m
  }

  translate(tx, ty) {
    this._m = mul(this._m, [1, 0, 0, 1, tx, ty])
  }

  rotate(theta) {
    const c = Math.cos(theta)
    const s = Math.sin(theta)
    this._m = mul(this._m, [c, s, -s, c, 0, 0])
  }

  _apply(x, y) {
    const [a, b, cc, d, e, f] = this._m
    return [a * x + cc * y + e, b * x + d * y + f]
  }

  _isAxisAligned() {
    const [a, b, cc, d] = this._m
    return Math.abs(b) < 1e-9 && Math.abs(cc) < 1e-9 && Math.abs(Math.abs(a) - 1) < 1e-9 && Math.abs(Math.abs(d) - 1) < 1e-9
  }

  _blendPixel(px, py, color) {
    const buf = this.buf
    if (px < 0 || py < 0 || px >= buf.width || py >= buf.height) return
    const alpha = color[3] * this.globalAlpha
    if (alpha <= 0) return
    const i = (py * buf.width + px) * 4
    const d = buf.data
    if (alpha >= 1) {
      d[i] = color[0]
      d[i + 1] = color[1]
      d[i + 2] = color[2]
      d[i + 3] = 255
    } else {
      const ia = 1 - alpha
      d[i] = color[0] * alpha + d[i] * ia
      d[i + 1] = color[1] * alpha + d[i + 1] * ia
      d[i + 2] = color[2] * alpha + d[i + 2] * ia
      d[i + 3] = Math.min(255, d[i + 3] + alpha * 255)
    }
  }

  _fillPoly(points, style) {
    const color = parseColor(style)
    if (color[3] <= 0) return
    // scanline even-odd fill
    let minY = Infinity
    let maxY = -Infinity
    for (const p of points) {
      if (p[1] < minY) minY = p[1]
      if (p[1] > maxY) maxY = p[1]
    }
    const y0 = Math.max(0, Math.ceil(minY - 0.5))
    const y1 = Math.min(this.buf.height - 1, Math.floor(maxY + 0.5))
    const n = points.length
    for (let y = y0; y <= y1; y++) {
      const sy = y + 0.5
      const xs = []
      for (let i = 0; i < n; i++) {
        const p1 = points[i]
        const p2 = points[(i + 1) % n]
        const y1v = p1[1]
        const y2v = p2[1]
        if ((y1v <= sy && y2v > sy) || (y2v <= sy && y1v > sy)) {
          const t = (sy - y1v) / (y2v - y1v)
          xs.push(p1[0] + t * (p2[0] - p1[0]))
        }
      }
      if (xs.length < 2) continue
      xs.sort((a, b) => a - b)
      for (let k = 0; k + 1 < xs.length; k += 2) {
        const xa = Math.max(0, Math.round(xs[k] - 0.5) + 0.5 - 0.5) // snap-ish
        const from = Math.max(0, Math.ceil(xs[k] - 0.5))
        const to = Math.min(this.buf.width - 1, Math.floor(xs[k + 1] - 0.5))
        for (let x = from; x <= to; x++) this._blendPixel(x, y, color)
      }
    }
  }

  clearRect(x, y, w, h) {
    // only used for full clears in our code (never) — treat as black fill
    const [x0, y0] = this._apply(x, y)
    const [x1, y1] = this._apply(x + w, y + h)
    const buf = this.buf
    for (let py = Math.max(0, Math.round(y0)); py < Math.min(buf.height, Math.round(y1)); py++) {
      for (let px = Math.max(0, Math.round(x0)); px < Math.min(buf.width, Math.round(x1)); px++) {
        const i = (py * buf.width + px) * 4
        buf.data[i] = 0
        buf.data[i + 1] = 0
        buf.data[i + 2] = 0
        buf.data[i + 3] = 0
      }
    }
  }

  fillRect(x, y, w, h) {
    if (this._isAxisAligned()) {
      const color = parseColor(this.fillStyle)
      const [tx, ty] = this._apply(x, y)
      const [sx, sy] = [Math.round(tx), Math.round(ty)]
      const sw = Math.round(w * Math.abs(this._m[0]))
      const sh = Math.round(h * Math.abs(this._m[3]))
      for (let py = sy; py < sy + sh; py++) {
        for (let px = sx; px < sx + sw; px++) {
          this._blendPixel(px, py, color)
        }
      }
    } else {
      const pts = [this._apply(x, y), this._apply(x + w, y), this._apply(x + w, y + h), this._apply(x, y + h)]
      this._fillPoly(pts, this.fillStyle)
    }
  }

  strokeRect(x, y, w, h) {
    const color = parseColor(this.strokeStyle)
    if (color[3] <= 0) return
    const rect = [
      this._apply(x, y),
      this._apply(x + w, y),
      this._apply(x + w, y + h),
      this._apply(x, y + h),
    ]
    const hw = this.lineWidth / 2
    for (let i = 0; i < 4; i++) {
      const p1 = rect[i]
      const p2 = rect[(i + 1) % 4]
      const dx = p2[0] - p1[0]
      const dy = p2[1] - p1[1]
      const len = Math.hypot(dx, dy) || 1
      const nx = (-dy / len) * hw
      const ny = (dx / len) * hw
      this._fillPoly(
        [
          [p1[0] + nx, p1[1] + ny],
          [p2[0] + nx, p2[1] + ny],
          [p2[0] - nx, p2[1] - ny],
          [p1[0] - nx, p1[1] - ny],
        ],
        this.strokeStyle
      )
    }
  }

  drawImage(src, dx = 0, dy = 0) {
    // only used as drawImage(bg, 0, 0) with identical size, identity transform
    const srcCtx = src.getContext('2d')
    const s = srcCtx.buf
    const buf = this.buf
    const [ox, oy] = this._apply(dx, dy)
    for (let y = 0; y < s.height; y++) {
      const py = Math.round(oy) + y
      if (py < 0 || py >= buf.height) continue
      for (let x = 0; x < s.width; x++) {
        const px = Math.round(ox) + x
        if (px < 0 || px >= buf.width) continue
        const si = (y * s.width + x) * 4
        const di = (py * buf.width + px) * 4
        const a = s.data[si + 3] / 255
        const ia = 1 - a
        buf.data[di] = s.data[si] * a + buf.data[di] * ia
        buf.data[di + 1] = s.data[si + 1] * a + buf.data[di + 1] * ia
        buf.data[di + 2] = s.data[si + 2] * a + buf.data[di + 2] * ia
        buf.data[di + 3] = Math.min(255, s.data[si + 3] + buf.data[di + 3] * ia)
      }
    }
  }

  createRadialGradient() {
    // vignette no-op for headless QA
    return { addColorStop: () => {} }
  }
}

function mul(m1, m2) {
  return [
    m1[0] * m2[0] + m1[2] * m2[1],
    m1[1] * m2[0] + m1[3] * m2[1],
    m1[0] * m2[2] + m1[2] * m2[3],
    m1[1] * m2[2] + m1[3] * m2[3],
    m1[0] * m2[4] + m1[2] * m2[5] + m1[4],
    m1[1] * m2[4] + m1[3] * m2[5] + m1[5],
  ]
}
