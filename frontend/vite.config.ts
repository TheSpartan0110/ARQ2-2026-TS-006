import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  server: {
    // 5173 is the redirect URI registered for the `eatsnow-web` OIDC client (Grafana uses 3000).
    port: 5173,
    strictPort: true,
    // Same-origin API calls through the API Gateway, the single entry point. The OIDC login itself runs on the
    // Gateway origin (http://localhost:8090).
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
      '/oauth2/jwks': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
    },
  },
})
