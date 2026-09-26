import { useEffect, useRef, useState } from 'react'
import { DIFFICULTIES } from '../game/ai.js'
import { sfx } from '../game/audio.js'

export default function Menu({ difficulty, onSelectDifficulty, onStart }) {
  const [sel, setSel] = useState(difficulty)
  const selRef = useRef(sel)
  selRef.current = sel

  const startWith = (i) => {
    sfx.uiSelect()
    onSelectDifficulty(i)
    onStart()
  }

  // Subscribe once; rapid keystrokes between renders are handled via the
  // functional setState + selRef (no stale closures).
  useEffect(() => {
    const onKey = (e) => {
      if (e.repeat) return
      if (e.code === 'ArrowUp' || e.code === 'KeyW') {
        e.preventDefault()
        setSel((s) => (s + DIFFICULTIES.length - 1) % DIFFICULTIES.length)
        sfx.uiMove()
      } else if (e.code === 'ArrowDown' || e.code === 'KeyS') {
        e.preventDefault()
        setSel((s) => (s + 1) % DIFFICULTIES.length)
        sfx.uiMove()
      } else if (e.code === 'Enter' || e.code === 'Space') {
        e.preventDefault()
        startWith(selRef.current)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className="screen">
      <div className="canvas-wrap menu-wrap">
        <div className="menu-inner">
          <h1 className="logo">GRID DUEL</h1>
          <div className="tagline">A LIGHT CYCLE DEATHMATCH</div>

          <div className="section-label">- SELECT YOUR OPPONENT -</div>
          <div className="diff-list" role="listbox" aria-label="difficulty">
            {DIFFICULTIES.map((d, i) => (
              <button
                key={d.key}
                className={'diff' + (i === sel ? ' sel' : '')}
                onMouseEnter={() => {
                  if (i !== sel) {
                    setSel(i)
                    sfx.uiMove()
                  }
                }}
                onClick={() => startWith(i)}
              >
                <span className="diff-name">{d.name}</span>
                <span className="diff-desc">{d.blurb}</span>
              </button>
            ))}
          </div>

          <button className="btn btn-start blink" onClick={() => startWith(sel)}>
            INSERT COIN - PRESS START
          </button>

          <div className="controls-hint">
            ARROWS / WASD — STEER · P — PAUSE · M — SOUND
          </div>
          <div className="controls-hint dim">
            FIRST TO 5 ROUNDS WINS · YELLOW BOLT = TURBO · PINK BLOCK = ERASES
            YOUR TRAIL
          </div>
        </div>
      </div>
      <div className="crt" />
    </div>
  )
}
