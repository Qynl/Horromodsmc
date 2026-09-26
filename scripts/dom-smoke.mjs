// jsdom smoke test: builds the real production bundle and drives the actual
// React app through a full game flow with synthetic keyboard events.
// The canvas is backed by the software rasterizer, so the real renderer runs.
//
//   node scripts/dom-smoke.mjs   (run `npm run build` variant first — see below)
//
// Flow tested: menu -> difficulty select -> start -> countdown -> running ->
// human crash (rides into wall) -> round result -> next round -> pause ->
// resume -> quit to title.

import fs from 'node:fs'
import { pathToFileURL } from 'node:url'
import { JSDOM } from 'jsdom'
import { SoftwareCtx } from './software-canvas.mjs'

const BUNDLE = new URL('../dist-dom/dom-entry.js', import.meta.url).pathname

if (!fs.existsSync(BUNDLE)) {
  console.error('bundle missing — run: npx vite build --config vite.dom-test.config.js')
  process.exit(1)
}

const dom = new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>', {
  url: 'http://localhost/',
  pretendToBeVisual: true,
})
const { window } = dom

globalThis.window = window
globalThis.document = window.document
Object.defineProperty(globalThis, 'navigator', { value: window.navigator, configurable: true })
globalThis.HTMLCanvasElement = window.HTMLCanvasElement
globalThis.KeyboardEvent = window.KeyboardEvent
// Drive RAF ourselves so timestamps share Node's performance.now() time base
// (jsdom's own performance.now() recurses through the global we can't replace).
const raf = (cb) => setTimeout(() => cb(performance.now()), 16)
globalThis.requestAnimationFrame = raf
globalThis.cancelAnimationFrame = (id) => clearTimeout(id)
window.requestAnimationFrame = raf
window.cancelAnimationFrame = (id) => clearTimeout(id)

// patch canvas with the software rasterizer
const ctxMap = new WeakMap()
window.HTMLCanvasElement.prototype.getContext = function (type) {
  if (type !== '2d') return null
  if (!ctxMap.has(this)) ctxMap.set(this, new SoftwareCtx(this))
  return ctxMap.get(this)
}

let failures = 0
const check = (name, cond, detail = '') => {
  if (cond) console.log(`  ok  ${name}`)
  else {
    failures++
    console.log(`  FAIL ${name} ${detail}`)
  }
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const key = (code) =>
  window.dispatchEvent(new window.KeyboardEvent('keydown', { code, bubbles: true }))
const text = () => document.body.textContent || ''
const sel = (q) => document.querySelector(q)

async function waitFor(desc, fn, timeoutMs = 30000, poll = 120) {
  const t0 = Date.now()
  while (Date.now() - t0 < timeoutMs) {
    if (fn()) return true
    await sleep(poll)
  }
  check(desc, false, 'timeout')
  return false
}

// --------------------------------------------------------------------------
console.log('== mount ==')
await import(pathToFileURL(BUNDLE))
await waitFor('app renders menu', () => text().includes('SELECT YOUR OPPONENT'))
check('menu shows difficulties', text().includes('MASTER CONTROL'))
check('default selection is PROGRAM', (sel('.diff.sel')?.textContent || '').includes('PROGRAM'))

console.log('== difficulty select ==')
key('ArrowDown')
await sleep(150)
check('arrow moves selection to SARK', (sel('.diff.sel')?.textContent || '').includes('SARK'))
key('ArrowDown')
await sleep(150)
check('arrow moves selection to MASTER CONTROL', (sel('.diff.sel')?.textContent || '').includes('MASTER CONTROL'))
key('ArrowUp')
key('ArrowUp')
key('ArrowUp') // wraparound: 0 -> 3
await sleep(150)
check('wraparound to USER', (sel('.diff.sel')?.textContent || '').includes('USER'))
key('ArrowDown') // back to PROGRAM
await sleep(150)

console.log('== start match ==')
key('Enter')
await waitFor('countdown appears', () => !!sel('.count'), 5000)
check('countdown starts at 3', sel('.count')?.textContent === '3')
check('canvas present', !!sel('canvas'))

console.log('== running ==')
await waitFor('countdown finished', () => !sel('.count'), 8000)
// let the human ride straight into the right wall (~3s) while PROGRAM survives
await waitFor('round result shown', () =>
  ['ROUND WON', 'ROUND LOST', 'DOUBLE DERESOLUTION'].some((t) => text().includes(t))
)
check('score line visible', /YOU [0-9] - [0-9] AI/.test(text()))

console.log('== next round ==')
await waitFor('next countdown appears', () => !!sel('.count'), 10000)
check('HUD shows round 2', text().includes('ROUND 2'))
await waitFor('countdown finished', () => !sel('.count'), 8000)

console.log('== steer input accepted (no crash) ==')
key('ArrowDown')
await sleep(400)
key('ArrowRight')
await sleep(400)
key('ArrowUp')
await sleep(400)
check('still mounted after steering', !!sel('canvas'))

console.log('== pause / resume ==')
key('KeyP')
await sleep(200)
check('paused overlay', text().includes('PAUSED'))
key('KeyP')
await sleep(200)
check('pause cleared', !text().includes('PAUSED'))

console.log('== quit to title ==')
key('Escape')
await sleep(200)
check('escape opens pause', text().includes('PAUSED'))
const quitBtn = [...document.querySelectorAll('button')].find((b) =>
  (b.textContent || '').includes('QUIT TO TITLE')
)
check('quit button exists', !!quitBtn)
quitBtn?.click()
await waitFor('back at menu', () => text().includes('SELECT YOUR OPPONENT'), 5000)
check('selection remembered (PROGRAM)', (sel('.diff.sel')?.textContent || '').includes('PROGRAM'))

console.log(failures === 0 ? '\nDOM SMOKE TEST PASSED' : `\n${failures} CHECKS FAILED`)
process.exit(failures === 0 ? 0 : 1)
