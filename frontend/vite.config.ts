import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'

// In development the API is proxied, so the browser only ever talks to one origin (no CORS setup needed).
const apiTarget = process.env.VITE_DEV_API_TARGET ?? 'http://localhost:8080'

// The proxy makes requests same-origin from the browser's point of view, so the Origin header is dropped. Otherwise
// opening the app via 127.0.0.1, a LAN IP or a different port would be rejected by the backend's CORS allow-list.
const proxy = {
  '/api': {
    target: apiTarget,
    changeOrigin: true,
    configure: (p: { on: (e: 'proxyReq', cb: (req: { removeHeader: (n: string) => void }) => void) => void }) =>
      p.on('proxyReq', (req) => req.removeHeader('origin')),
  },
}

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy,
  },
  preview: {
    port: 4173,
    proxy,
  },
  build: {
    chunkSizeWarningLimit: 900,
  },
})
