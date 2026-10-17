import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173, strictPort: true, proxy: { '/api': process.env.BACKEND_URL ?? 'http://localhost:8081' },
    // R14: el almacenamiento de archivos (.data) nunca se sirve desde el navegador; sólo a través de /api.
    fs: { strict: true, deny: ['.env', '.env.*', '*.{crt,pem}', '**/.git/**', '**/.data/**'] },
  },
});
