// Test entry for the jsdom smoke test (built separately, loaded by dom-smoke.mjs).
import { createRoot } from 'react-dom/client'
import App from '../src/App.jsx'

createRoot(document.getElementById('root')).render(<App />)
