import { test, expect } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { platform } from 'node:os';
import { fileURLToPath } from 'node:url';

const password = 'una frase de prueba segura';

function provisionCompanyAdmin(email: string) {
  const env = { ...process.env, E2E_ADMIN_EMAIL: email, E2E_ADMIN_PASSWORD: password };
  if (platform() === 'win32') {
    execFileSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
      fileURLToPath(new URL('./provision-fixture.ps1', import.meta.url))], {
      env, timeout: 60_000, windowsHide: true, stdio: 'pipe',
    });
    return;
  }
  execFileSync('bash', [fileURLToPath(new URL('./provision-fixture.sh', import.meta.url))], {
    env, timeout: 60_000, stdio: 'pipe',
  });
}

test('script crea empresa y Administrador que puede iniciar y cerrar sesión', async ({ page }) => {
  test.setTimeout(90_000);
  const email = `admin-${crypto.randomUUID()}@example.test`;
  provisionCompanyAdmin(email);
  await page.goto('/ingresar');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).toHaveURL(/\/mi-cuenta$/);
  await expect(page.getByText('Administrador', { exact: true })).toBeVisible();
  await expect(page.getByText('Empresa E2E', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Verificar acceso' }).click();
  await expect(page.getByRole('status')).toContainText('Acceso verificado');
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page).toHaveURL(/\/ingresar$/);
  await page.goto('/mi-cuenta');
  await expect(page).toHaveURL(/\/ingresar$/);
  expect((await page.request.get('/api/account')).status()).toBe(401);
});

test('registro de Talento, login, ruta protegida y logout', async ({ page }) => {
  const email = `e2e-${crypto.randomUUID()}@example.test`;
  await page.goto('/mi-cuenta');
  await expect(page).toHaveURL(/\/ingresar$/);
  await page.getByRole('link', { name: 'Registrate como Talento' }).click();
  await expect(page.getByRole('heading', { name: 'Creá tu cuenta de Talento' })).toBeVisible();
  await expect(page.getByRole('combobox')).toHaveCount(0);
  await page.getByLabel('Nombre', { exact: true }).fill('Ana');
  await page.getByLabel('Apellido').fill('Prueba');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Crear cuenta de Talento' }).click();
  await expect(page.getByRole('status')).toContainText('Solicitud recibida');
  await page.getByRole('link', { name: 'Iniciá sesión' }).click();
  await expect(page.getByRole('heading', { name: 'Iniciá sesión' })).toBeVisible();
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña', { exact: true }).fill('incorrecta');
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Correo o contraseña incorrectos');
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).toHaveURL(/\/mi-cuenta$/);
  await expect(page.getByRole('heading', { name: 'Hola, Ana' })).toBeVisible();
  await page.reload();
  await expect(page.getByText('Talento', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Verificar acceso' }).click();
  await expect(page.getByRole('status')).toContainText('Acceso verificado');
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page).toHaveURL(/\/ingresar$/);
  await page.goto('/mi-cuenta');
  await expect(page).toHaveURL(/\/ingresar$/);
  expect((await page.request.get('/api/account')).status()).toBe(401);
});

test('registro duplicado muestra exactamente el mismo mensaje', async ({ page }) => {
  const email = `duplicate-${crypto.randomUUID()}@example.test`;
  await page.goto('/registro');
  for (const address of [email, email.toUpperCase()]) {
    await page.getByLabel('Nombre', { exact: true }).fill('Ana');
    await page.getByLabel('Apellido').fill('Prueba');
    await page.getByLabel('Correo electrónico').fill(address);
    await page.getByLabel('Contraseña', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'Crear cuenta de Talento' }).click();
    await expect(page.getByRole('status')).toHaveText('Solicitud recibida. Si los datos permiten crear una cuenta, podrás iniciar sesión con las credenciales indicadas.');
  }
});

test('formulario usable en pantalla de celular', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto('/registro');
  await expect(page.getByRole('button', { name: 'Crear cuenta de Talento' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
