// GRID DUEL — retro sound engine.
// Everything is synthesized with WebAudio oscillators/noise. No audio files.
// Audio contexts must be created after a user gesture; call initAudio() on
// the first keydown/pointerdown (App does this).

let ctx = null
let master = null
let muted = false
let hum = null

export function initAudio() {
  if (ctx) {
    if (ctx.state === 'suspended') ctx.resume()
    return
  }
  const AC = window.AudioContext || window.webkitAudioContext
  if (!AC) return
  ctx = new AC()
  master = ctx.createGain()
  master.gain.value = muted ? 0 : 1
  master.connect(ctx.destination)
}

export function setMuted(m) {
  muted = m
  if (master) master.gain.value = m ? 0 : 1
}

export function isMuted() {
  return muted
}

function tone({ freq = 440, end = null, dur = 0.1, type = 'square', vol = 0.12, when = 0 }) {
  if (!ctx || muted) return
  const t0 = ctx.currentTime + when
  const o = ctx.createOscillator()
  const g = ctx.createGain()
  o.type = type
  o.frequency.setValueAtTime(freq, t0)
  if (end !== null && end !== freq) {
    o.frequency.exponentialRampToValueAtTime(Math.max(end, 1), t0 + dur)
  }
  g.gain.setValueAtTime(vol, t0)
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur)
  o.connect(g)
  g.connect(master)
  o.start(t0)
  o.stop(t0 + dur + 0.02)
}

function noiseBurst({ dur = 0.35, vol = 0.25, cutoff = 900 }) {
  if (!ctx || muted) return
  const len = Math.floor(ctx.sampleRate * dur)
  const buf = ctx.createBuffer(1, len, ctx.sampleRate)
  const d = buf.getChannelData(0)
  for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / len)
  const src = ctx.createBufferSource()
  src.buffer = buf
  const f = ctx.createBiquadFilter()
  f.type = 'lowpass'
  f.frequency.value = cutoff
  const g = ctx.createGain()
  g.gain.value = vol
  src.connect(f)
  f.connect(g)
  g.connect(master)
  src.start()
}

export const sfx = {
  turn: () => tone({ freq: 880, dur: 0.025, vol: 0.035 }),
  turnAi: () => tone({ freq: 600, dur: 0.025, vol: 0.03 }),
  crash: () => {
    noiseBurst({ dur: 0.45, vol: 0.3, cutoff: 1200 })
    tone({ freq: 220, end: 40, dur: 0.4, type: 'sawtooth', vol: 0.18 })
  },
  pickupTurbo: () => tone({ freq: 300, end: 1400, dur: 0.22, type: 'sawtooth', vol: 0.1 }),
  pickupEraser: () => tone({ freq: 1100, end: 180, dur: 0.28, type: 'triangle', vol: 0.13 }),
  count: () => tone({ freq: 440, dur: 0.09, vol: 0.14 }),
  go: () => tone({ freq: 880, dur: 0.22, vol: 0.16 }),
  roundWin: () => [523, 659, 784].forEach((f, i) => tone({ freq: f, dur: 0.09, vol: 0.12, when: i * 0.09 })),
  roundLose: () => [330, 262, 196].forEach((f, i) => tone({ freq: f, dur: 0.12, vol: 0.12, when: i * 0.1 })),
  draw: () => tone({ freq: 392, dur: 0.18, vol: 0.1 }),
  matchWin: () => [523, 659, 784, 1046, 1318].forEach((f, i) => tone({ freq: f, dur: 0.11, vol: 0.13, when: i * 0.11 })),
  matchLose: () => [392, 311, 262, 196].forEach((f, i) => tone({ freq: f, dur: 0.16, vol: 0.13, when: i * 0.14, type: 'sawtooth' })),
  uiMove: () => tone({ freq: 520, dur: 0.03, vol: 0.07 }),
  uiSelect: () => tone({ freq: 740, dur: 0.07, vol: 0.1 }),
  pause: () => tone({ freq: 300, dur: 0.07, vol: 0.1 }),
}

// Subtle engine hum while a round runs.
export function startHum() {
  if (!ctx || muted || hum) return
  const o1 = ctx.createOscillator()
  o1.type = 'sawtooth'
  o1.frequency.value = 52
  const o2 = ctx.createOscillator()
  o2.type = 'sawtooth'
  o2.frequency.value = 54.5
  const f = ctx.createBiquadFilter()
  f.type = 'lowpass'
  f.frequency.value = 220
  const g = ctx.createGain()
  g.gain.value = 0.045
  o1.connect(f)
  o2.connect(f)
  f.connect(g)
  g.connect(master)
  o1.start()
  o2.start()
  hum = { o1, o2 }
}

export function stopHum() {
  if (!hum) return
  try {
    hum.o1.stop()
    hum.o2.stop()
  } catch {
    /* already stopped */
  }
  hum = null
}

export function setHumTurbo(on) {
  if (!hum || !ctx) return
  const target = on ? 110 : 52
  hum.o1.frequency.setTargetAtTime(target, ctx.currentTime, 0.08)
  hum.o2.frequency.setTargetAtTime(target + 2.5, ctx.currentTime, 0.08)
}
