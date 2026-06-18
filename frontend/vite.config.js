import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 4200,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Return 502 (bad gateway) when the backend is unreachable, so the
        // frontend can tell "backend is down" apart from a real backend 500
        // and fall back to mock data (see src/services/index.js).
        configure: (proxy) => {
          proxy.on('error', (_err, _req, res) => {
            if (res && !res.headersSent) {
              res.writeHead(502, { 'Content-Type': 'application/json' })
            }
            if (res) res.end(JSON.stringify({ error: 'backend unreachable' }))
          })
        },
      },
    },
  },
})
