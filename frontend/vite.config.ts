import { randomBytes } from 'node:crypto';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// OWASP Secure Headers Project: la app no usa ninguna de estas APIs del navegador, así que todas quedan bloqueadas.
const permissionsPolicy = [
  'accelerometer', 'autoplay', 'camera', 'clipboard-read', 'clipboard-write', 'cross-origin-isolated',
  'display-capture', 'encrypted-media', 'fullscreen', 'gamepad', 'geolocation', 'gyroscope', 'hid',
  'idle-detection', 'interest-cohort', 'keyboard-map', 'magnetometer', 'microphone', 'midi', 'payment',
  'picture-in-picture', 'publickey-credentials-get', 'screen-wake-lock', 'serial', 'sync-xhr', 'unload', 'usb',
  'web-share', 'xr-spatial-tracking',
].map((feature) => `${feature}=()`).join(', ');

function securityHeaders(nonce?: string): Record<string, string> {
  // En desarrollo Vite inyecta un script inline (React Refresh) y estilos con <style>; el nonce los habilita
  // sin recurrir a 'unsafe-inline'. El build de producción no tiene nada inline.
  const inline = nonce ? ` 'nonce-${nonce}'` : '';
  return {
    'Content-Security-Policy': [
      "default-src 'self'", `script-src 'self'${inline}`, `style-src 'self'${inline}`, "img-src 'self' data:",
      "connect-src 'self'", "font-src 'self'", "object-src 'none'", "base-uri 'self'", "form-action 'self'",
      "frame-ancestors 'none'",
    ].join('; '),
    'Strict-Transport-Security': 'max-age=63072000; includeSubDomains',
    'X-Frame-Options': 'DENY',
    'X-Content-Type-Options': 'nosniff',
    'X-Permitted-Cross-Domain-Policies': 'none',
    'Referrer-Policy': 'no-referrer',
    'Cross-Origin-Embedder-Policy': 'require-corp',
    'Cross-Origin-Opener-Policy': 'same-origin',
    'Cross-Origin-Resource-Policy': 'same-origin',
    'Permissions-Policy': permissionsPolicy,
    'Cache-Control': 'no-store, max-age=0',
    'X-DNS-Prefetch-Control': 'off',
  };
}

export default defineConfig(({ command }) => {
  // Nuevo en cada arranque del servidor de desarrollo; nunca se usa en el build.
  const nonce = command === 'serve' ? randomBytes(16).toString('base64') : undefined;
  return {
    plugins: [react()],
    html: { cspNonce: nonce },
    server: {
      port: 5173, strictPort: true, proxy: { '/api': process.env.BACKEND_URL ?? 'http://localhost:8081' },
      // R14: el almacenamiento de archivos (.data) nunca se sirve desde el navegador; sólo a través de /api.
      fs: { strict: true, deny: ['.env', '.env.*', '*.{crt,pem}', '**/.git/**', '**/.data/**'] },
      headers: securityHeaders(nonce),
    },
    preview: { headers: securityHeaders() },
  };
});
