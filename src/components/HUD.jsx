import { DIFFICULTIES } from '../game/ai.js'

function Pips({ n, side }) {
  return (
    <span className={'pips ' + side}>
      {[0, 1, 2, 3, 4].map((i) => (
        <span key={i} className={'pip ' + side + (i < n ? ' on' : '')} />
      ))}
    </span>
  )
}

export default function HUD({ scores, difficulty, roundIndex, muted, onToggleMute }) {
  const d = DIFFICULTIES[difficulty]
  return (
    <div className="hud">
      <div className="hud-side">
        <span className="hud-label cyan">YOU</span>
        <Pips n={scores[0]} side="cyan" />
      </div>
      <div className="hud-mid">
        <span>ROUND {roundIndex + 1}</span>
        <span className="hud-dim">VS {d.name}</span>
      </div>
      <div className="hud-side">
        <Pips n={scores[1]} side="orange" />
        <span className="hud-label orange">AI</span>
      </div>
      <button className="mute-btn" onClick={onToggleMute} title="Toggle sound (M)">
        {muted ? 'SND OFF' : 'SND ON'}
      </button>
    </div>
  )
}
