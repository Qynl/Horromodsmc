import { DIFFICULTIES } from '../game/ai.js'
import { sfx } from '../game/audio.js'

export default function Overlays({ ui, difficulty, onResume, onRematch, onMenu }) {
  const d = DIFFICULTIES[difficulty]

  if (ui.paused) {
    return (
      <div className="overlay">
        <div className="big">PAUSED</div>
        <div className="overlay-btns">
          <button
            className="btn"
            onClick={(e) => {
              e.currentTarget.blur()
              onResume()
            }}
          >
            RESUME
          </button>
          <button
            className="btn ghost"
            onClick={(e) => {
              e.currentTarget.blur()
              sfx.uiSelect()
              onMenu()
            }}
          >
            QUIT TO TITLE
          </button>
        </div>
        <div className="controls-hint dim">P / ESC — RESUME</div>
      </div>
    )
  }

  if (ui.phase === 'countdown') {
    return (
      <div className="overlay clear">
        <div className="count" key={ui.countdown}>
          {ui.countdown > 0 ? ui.countdown : 'GO!'}
        </div>
      </div>
    )
  }

  if (ui.phase === 'roundover') {
    const title =
      ui.winner === 'draw' ? 'DOUBLE DERESOLUTION' : ui.winner === 0 ? 'ROUND WON' : 'ROUND LOST'
    const cls = ui.winner === 'draw' ? 'magenta' : ui.winner === 0 ? 'cyan' : 'orange'
    return (
      <div className="overlay">
        <div className={'big ' + cls}>{title}</div>
        <div className="score-line">
          YOU {ui.scores[0]} - {ui.scores[1]} AI
        </div>
      </div>
    )
  }

  if (ui.phase === 'matchover') {
    const won = ui.matchWinner === 0
    return (
      <div className="overlay">
        <div className={'big ' + (won ? 'cyan' : 'orange')}>{won ? 'VICTORY!' : 'DERESOLVED'}</div>
        <div className="score-line">
          YOU {ui.scores[0]} - {ui.scores[1]} {d.name}
        </div>
        <div className="overlay-btns">
          <button
            className="btn"
            onClick={(e) => {
              e.currentTarget.blur()
              sfx.uiSelect()
              onRematch()
            }}
          >
            REMATCH [ENTER]
          </button>
          <button
            className="btn ghost"
            onClick={(e) => {
              e.currentTarget.blur()
              sfx.uiSelect()
              onMenu()
            }}
          >
            CHANGE OPPONENT [ESC]
          </button>
        </div>
      </div>
    )
  }

  return null
}
