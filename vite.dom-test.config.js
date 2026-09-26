// Config for the jsdom smoke test bundle (scripts/dom-smoke.mjs).
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist-dom',
    emptyOutDir: true,
    rollupOptions: {
      input: 'scripts/dom-entry.jsx',
      output: {
        entryFileNames: 'dom-entry.js',
      },
    },
  },
})
