import { useEffect, useState } from 'react'
import Menu from './components/Menu.jsx'
import Game from './components/Game.jsx'
import { initAudio } from './game/audio.js'

export default function App() {
  const [screen, setScreen] = useState('menu')
  const [difficulty, setDifficulty] = useState(1) // PROGRAM by default
  const [matchKey, setMatchKey] = useState(0)

  // Browsers only allow audio after a user gesture — warm it up on the
  // first interaction anywhere.
  useEffect(() => {
    const warm = () => initAudio()
    window.addEventListener('pointerdown', warm)
    window.addEventListener('keydown', warm)
    return () => {
      window.removeEventListener('pointerdown', warm)
      window.removeEventListener('keydown', warm)
    }
  }, [])

  return (
    <div className="cabinet">
      <div className="marquee">
        <span className="marquee-deco">&lt;</span> GRID DUEL{' '}
        <span className="marquee-deco">&gt;</span>
      </div>
      {screen === 'menu' ? (
        <Menu
          difficulty={difficulty}
          onSelectDifficulty={setDifficulty}
          onStart={() => {
            setMatchKey((k) => k + 1)
            setScreen('game')
          }}
        />
      ) : (
        <Game
          key={matchKey}
          difficulty={difficulty}
          onExit={() => setScreen('menu')}
        />
      )}
      <div className="footer-hint">
        ARROWS / WASD — STEER &nbsp;·&nbsp; P — PAUSE &nbsp;·&nbsp; M — SOUND
      </div>
    </div>
  )
}
