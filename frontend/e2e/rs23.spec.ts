import { test, expect } from '@playwright/test';

test('RS23: cada operación pide contraseña y cancelar no modifica el staff', async ({ page }) => {
  const member = { usuarioId: 'member', empresaId: 'company', correo: 'staff@example.test', nombre: 'Staff', apellido: 'Prueba', rol: 'EDITOR', estado: 'ACTIVA' };
  const writes: string[] = [];
  let confirmations = 0;
  await page.route('**/api/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/account') return route.fulfill({ json: { id: 'admin', rol: 'ADMIN', tipoCuenta: 'STAFF' } });
    if (path === '/api/staff') return route.fulfill({ json: [member] });
    if (path === '/api/auth/csrf') return route.fulfill({ json: { token: 'csrf', headerName: 'X-CSRF-TOKEN' } });
    if (path === '/api/auth/reauthenticate') {
      confirmations++;
      return request.postDataJSON().password === 'correcta'
        ? route.fulfill({ status: 204 })
        : route.fulfill({ status: 401, json: { message: 'Credenciales inválidas' } });
    }
    writes.push(path);
    if (path.endsWith('/rol')) member.rol = request.postDataJSON().rol;
    if (path.endsWith('/baja')) member.estado = 'BAJA';
    return route.fulfill({ status: 204 });
  });
  await page.goto('/staff');
  const row = page.locator('.staff-member');
  const dialog = page.getByRole('dialog');
  await row.getByLabel('Rol', { exact: true }).selectOption('RECLUTADOR');
  for (const action of ['Asignar rol', 'Dar de baja']) {
    await row.getByRole('button', { name: action, exact: true }).click();
    await expect(dialog.getByLabel('Tu contraseña de administrador')).toBeVisible();
    await dialog.getByRole('button', { name: 'Cancelar', exact: true }).click();
    await expect(dialog).toHaveCount(0);
    expect(writes).toEqual([]);
    expect(confirmations).toBe(0);
  }
  await row.getByRole('button', { name: 'Asignar rol' }).click();
  await dialog.getByLabel('Tu contraseña de administrador').fill('incorrecta');
  await dialog.getByRole('button', { name: 'Confirmar operación' }).click();
  await expect(page.getByRole('alert')).toContainText('Credenciales inválidas');
  expect(writes).toEqual([]);
  await dialog.getByLabel('Tu contraseña de administrador').fill('correcta');
  await dialog.getByRole('button', { name: 'Confirmar operación' }).click();
  await expect(dialog).toHaveCount(0);
  expect(writes).toEqual(['/api/staff/member/rol']);
  await row.getByRole('button', { name: 'Dar de baja' }).click();
  await expect(dialog.getByLabel('Tu contraseña de administrador')).toHaveValue('');
  await dialog.getByLabel('Tu contraseña de administrador').fill('correcta');
  await dialog.getByRole('button', { name: 'Confirmar operación' }).click();
  await expect(row).toContainText('Baja');
  expect(writes).toEqual(['/api/staff/member/rol', '/api/staff/member/baja']);
  expect(confirmations).toBe(3);
});
