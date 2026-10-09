import { test, expect } from '@playwright/test';

test('RS16: eliminar la propia cuenta requiere confirmación y termina la sesión', async ({ page }) => {
  let deleted = false;
  let deletions = 0;
  await page.route('**/api/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/auth/csrf') {
      return route.fulfill({ json: { token: 'csrf', headerName: 'X-CSRF-TOKEN' } });
    }
    if (path === '/api/account' && request.method() === 'DELETE') {
      expect(request.headers()['x-csrf-token']).toBe('csrf');
      deleted = true; deletions++;
      return route.fulfill({ status: 204 });
    }
    if (path === '/api/account') {
      return deleted ? route.fulfill({ status: 401, json: { message: 'Sesión inválida' } })
        : route.fulfill({ json: { id: 'member', nombre: 'Reclutador', apellido: 'Prueba', correo: 'staff@example.test', tipoCuenta: 'STAFF', rol: 'RECLUTADOR' } });
    }
    return route.fulfill({ status: 404 });
  });
  await page.goto('/mi-cuenta');
  await page.getByRole('button', { name: 'Eliminar mi cuenta', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('button', { name: 'Cancelar', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  expect(deletions).toBe(0);
  await expect(page).toHaveURL(/\/mi-cuenta$/);
  await page.getByRole('button', { name: 'Eliminar mi cuenta', exact: true }).click();
  await dialog.getByRole('button', { name: 'Confirmar eliminación', exact: true }).click();
  await expect(page).toHaveURL(/\/ingresar$/);
  expect(deletions).toBe(1);
});
