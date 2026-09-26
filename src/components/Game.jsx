import { useEffect, useRef, useState } from 'react'
import {
  createRound,
  stepTick,
  queueDir,
  DIRS,
  TICK_MS,
  WIN_SCORE,
} from '../game/engine.js'
import { chooseDir } from '../game/ai.js'
import { createRenderer } from '../render/renderer.js'
import {
  sfx,
  startHum,
  stopHum,
  setHumTurbo,
  isMuted,
  setMuted,
} from '../game/audio.js'
import HUD from './HUD.jsx'
import Overlays from './Overlays.jsx'

const AI_INDEX = 1
const COUNTDOWN_STEP = 650 // ms per number (3, 2, 1, GO)
const COUNTDOWN_TOTAL = COUNTDOWN_STEP * 4
const ROUNDOVER_MS = 1400 // how long the round-result overlay stays

const KEY_DIRS = {
  ArrowUp: DIRS.up,
  KeyW: DIRS.up,
  ArrowRight: DIRS.right,
  KeyD: DIRS.right,
  ArrowDown: DIRS.down,
  KeyS: DIRS.down,
  ArrowLeft: DIRS.left,
  KeyA: DIRS.left,
}

const FRESH_UI = {
  phase: 'countdown',
  scores: [0, 0],
  roundIndex: 0,
  countdown: 3,
  winner: null,
  matchWinner: null,
  paused: false,
}

export default function Game({ difficulty, onExit }) {
  const canvasRef = useRef(null)
  const apiRef = useRef(null)
  const [ui, setUi] = useState({ ...FRESH_UI, muted: isMuted() })
  const [runId, setRunId] = useState(0)

  useEffect(() => {
    const canvas = canvasRef.current
    const renderer = createRenderer(canvas)
    const diffKey = ['user', 'program', 'sark', 'mcp'][difficulty] || 'program'

    let state = createRound([0, 0], 0)
    let raf = 0
    let last = performance.now()
    let acc = 0
    let countdownElapsed = 0
    let roundoverElapsed = 0
    let lastCountdownShown = 4
    let paused = false
    let phase = 'countdown' // mirrors state.phase + 'matchover'
    let matchDone = false
    let matchWinner = null

    setUi({ ...FRESH_UI, muted: isMuted() })

    const setUiLite = (patch) => setUi((u) => ({ ...u, ...patch }))

    const processEvents = (events) => {
      for (const e of events) {
        if (e.type === 'turn') {
          if (e.player === 1) sfx.turn()
          else sfx.turnAi()
        } else if (e.type === 'crash') {
          sfx.crash()
          renderer.explode(e.cell, e.player)
        } else if (e.type === 'pickup') {
          if (e.powerup === 'turbo') sfx.pickupTurbo()
          else sfx.pickupEraser()
        } else if (e.type === 'roundend') {
          stopHum()
          const w = e.winner
          if (w === 'draw') sfx.draw()
          else if (w === 0) sfx.roundWin()
          else sfx.roundLose()
          roundoverElapsed = 0
          setUiLite({ phase: 'roundover', winner: w, scores: state.scores.slice() })
          if (w !== 'draw' && state.scores[w] >= WIN_SCORE) {
            matchDone = true
            matchWinner = w
          }
        }
      }
    }

    const doTick = () => {
      state.players[AI_INDEX].dir = chooseDir(state, AI_INDEX, diffKey)
      processEvents(stepTick(state))
    }

    const startRound = (roundIndex) => {
      state = createRound(state.scores, roundIndex)
      renderer.reset()
      acc = 0
      countdownElapsed = 0
      lastCountdownShown = 4
      phase = 'countdown'
      setUiLite({ phase: 'countdown', roundIndex, countdown: 3, winner: null })
    }

    const anyTurbo = () =>
      state.players.some((p) => p.alive && state.tick < p.turboUntil)

    const setPaused = (p) => {
      if (phase === 'matchover') return
      if (paused === p) return
      paused = p
      if (p) stopHum()
      else if (phase === 'running') startHum()
      setUiLite({ paused: p })
      sfx.pause()
    }

    const loop = (now) => {
      raf = requestAnimationFrame(loop)
      const dt = Math.min(now - last, 250) // clamp huge gaps (tab switch)
      last = now

      if (!paused) {
        if (phase === 'countdown') {
          countdownElapsed += dt
          const n = 3 - Math.floor(countdownElapsed / COUNTDOWN_STEP)
          if (n !== lastCountdownShown && n >= 0 && n <= 3) {
            lastCountdownShown = n
            if (n > 0) sfx.count()
            else sfx.go()
            setUiLite({ countdown: n })
          }
          if (countdownElapsed >= COUNTDOWN_TOTAL) {
            phase = 'running'
            state.phase = 'running'
            acc = 0
            startHum()
            setUiLite({ phase: 'running' })
          }
        } else if (phase === 'running') {
          acc += dt
          let guard = 0
          while (acc >= TICK_MS && phase === 'running' && guard++ < 6) {
            acc -= TICK_MS
            doTick()
            if (state.phase === 'roundover') phase = 'roundover'
          }
          setHumTurbo(anyTurbo())
        } else if (phase === 'roundover') {
          roundoverElapsed += dt
          if (roundoverElapsed >= ROUNDOVER_MS) {
            if (matchDone) {
              phase = 'matchover'
              setUiLite({ phase: 'matchover', matchWinner })
              if (matchWinner === 0) sfx.matchWin()
              else sfx.matchLose()
            } else {
              startRound(state.roundIndex + 1)
            }
          }
        }
        // phase 'matchover': frozen frame, overlay handles the rest
      }

      renderer.drawFrame(state, now)
    }

    const onKeyDown = (e) => {
      if (e.repeat) return
      const dir = KEY_DIRS[e.code]
      if (dir) {
        e.preventDefault()
        if (state.phase === 'running' || state.phase === 'countdown') {
          queueDir(state.players[0], dir)
        }
        return
      }
      if (e.code === 'KeyP' || e.code === 'Escape') {
        if (phase === 'matchover') {
          if (e.code === 'Escape') onExit()
          return
        }
        e.preventDefault()
        setPaused(!paused)
      } else if (e.code === 'KeyM') {
        toggleMute()
      } else if (e.code === 'Enter' && phase === 'matchover') {
        rematch()
      }
    }

    const onBlur = () => {
      if (phase === 'running') setPaused(true)
    }

    const toggleMute = () => {
      const m = !isMuted()
      setMuted(m)
      setUiLite({ muted: m })
    }

    const rematch = () => {
      stopHum()
      setRunId((r) => r + 1)
    }

    apiRef.current = {
      togglePause: () => setPaused(!paused),
      rematch,
    }

    window.addEventListener('keydown', onKeyDown)
    window.addEventListener('blur', onBlur)
    raf = requestAnimationFrame(loop)

    return () => {
      cancelAnimationFrame(raf)
      window.removeEventListener('keydown', onKeyDown)
      window.removeEventListener('blur', onBlur)
      stopHum()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [runId, difficulty])

  return (
    <div className="screen">
      <div className="canvas-wrap">
        <canvas ref={canvasRef} />
        <Overlays
          ui={ui}
          difficulty={difficulty}
          onResume={() => apiRef.current && apiRef.current.togglePause()}
          onRematch={() => setRunId((r) => r + 1)}
          onMenu={onExit}
        />
      </div>
      <div className="crt" />
      <HUD
        scores={ui.scores}
        difficulty={difficulty}
        roundIndex={ui.roundIndex}
        muted={ui.muted}
        onToggleMute={() => {
          const m = !isMuted()
          setMuted(m)
          setUi((u) => ({ ...u, muted: m }))
        }}
      />
    </div>
  )
}
