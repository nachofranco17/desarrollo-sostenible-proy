import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// El servidor frontend reenvía /api al backend: el navegador sólo habla con un origen,
// así las cookies que agregue el inicio de sesión (RF1) quedan en el mismo sitio.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
