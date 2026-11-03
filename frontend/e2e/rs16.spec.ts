import { test, expect } from '@playwright/test';

test('RS16/RS23: eliminar la propia cuenta exige contraseña y termina la sesión', async ({ page }) => {
  let deleted = false;
  let deletions = 0;
  let confirmations = 0;
  let verified = false;
  await page.route('**/api/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/auth/csrf') {
      return route.fulfill({ json: { token: 'csrf', headerName: 'X-CSRF-TOKEN' } });
    }
    if (path === '/api/auth/reauthenticate') {
      expect(request.headers()['x-csrf-token']).toBe('csrf');
      confirmations++;
      verified = request.postDataJSON().password === 'correcta';
      return verified ? route.fulfill({ status: 204 })
        : route.fulfill({ status: 401, json: { message: 'Credenciales inválidas' } });
    }
    if (path === '/api/account' && request.method() === 'DELETE') {
      expect(request.headers()['x-csrf-token']).toBe('csrf');
      expect(verified).toBe(true);
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
  await dialog.getByLabel('Confirmá tu contraseña').fill('no debe enviarse');
  await dialog.getByRole('button', { name: 'Cancelar', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  expect(deletions).toBe(0);
  expect(confirmations).toBe(0);
  await expect(page).toHaveURL(/\/mi-cuenta$/);
  await page.getByRole('button', { name: 'Eliminar mi cuenta', exact: true }).click();
  await expect(dialog.getByLabel('Confirmá tu contraseña')).toHaveValue('');
  await dialog.getByLabel('Confirmá tu contraseña').fill('incorrecta');
  await dialog.getByRole('button', { name: 'Confirmar eliminación', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('No pudimos verificar tu identidad');
  await expect(dialog.getByLabel('Confirmá tu contraseña')).toHaveValue('');
  expect(deletions).toBe(0);
  await dialog.getByLabel('Confirmá tu contraseña').fill('correcta');
  await dialog.getByRole('button', { name: 'Confirmar eliminación', exact: true }).click();
  await expect(page).toHaveURL(/\/ingresar$/);
  expect(deletions).toBe(1);
  expect(confirmations).toBe(2);
});
