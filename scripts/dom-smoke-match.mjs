// jsdom match-flow test: full match (human loses 5 rounds to MASTER CONTROL)
// -> match-over overlay -> rematch via Enter -> quit to title.
// Requires dist-dom bundle: npx vite build --config vite.dom-test.config.js

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
const raf = (cb) => setTimeout(() => cb(performance.now()), 16)
globalThis.requestAnimationFrame = raf
globalThis.cancelAnimationFrame = (id) => clearTimeout(id)
window.requestAnimationFrame = raf
window.cancelAnimationFrame = (id) => clearTimeout(id)

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

async function waitFor(desc, fn, timeoutMs = 60000, poll = 150) {
  const t0 = Date.now()
  while (Date.now() - t0 < timeoutMs) {
    if (fn()) return true
    await sleep(poll)
  }
  check(desc, false, 'timeout')
  return false
}

await import(pathToFileURL(BUNDLE))
await waitFor('menu renders', () => text().includes('SELECT YOUR OPPONENT'))

// select MASTER CONTROL (index 3): default is PROGRAM (1) -> 2 downs
for (let i = 0; i < 2; i++) {
  key('ArrowDown')
  await sleep(200)
}
check('selected MASTER CONTROL', (sel('.diff.sel')?.textContent || '').includes('MASTER CONTROL'))
key('Enter')

// Human never steers -> rides into the wall every round -> loses 5 rounds.
await waitFor('match over (DEFEAT)', () => text().includes('DERESOLVED'), 120000)
check('final score 0-5', /YOU 0 - 5 MASTER CONTROL/.test(text()), text().match(/YOU \d - \d/)?.[0] || '?')
check('rematch button', text().includes('REMATCH'))
check('change opponent button', text().includes('CHANGE OPPONENT'))

console.log('== rematch via Enter ==')
key('Enter')
await waitFor('rematch countdown starts', () => !!sel('.count'), 8000)
check('scores reset', !/YOU [1-9]/.test(text()))

console.log('== quit via Escape at match over (fast-forward: lose 5 again) ==')
await waitFor('countdown finished', () => !sel('.count') && !!sel('canvas'), 10000)
await waitFor('match over again', () => text().includes('DERESOLVED'), 120000)
key('Escape')
await waitFor('back at menu', () => text().includes('SELECT YOUR OPPONENT'), 5000)
check('difficulty still selected', (sel('.diff.sel')?.textContent || '').includes('MASTER CONTROL'))

console.log(failures === 0 ? '\nMATCH FLOW TEST PASSED' : `\n${failures} CHECKS FAILED`)
process.exit(failures === 0 ? 0 : 1)
