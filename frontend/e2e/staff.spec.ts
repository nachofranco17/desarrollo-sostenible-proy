import { test, expect, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { readdirSync, readFileSync } from 'node:fs';
import { platform } from 'node:os';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const password = 'una frase de prueba segura';
async function login(page: Page, email: string) {
  await page.goto('/ingresar');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).toHaveURL(/\/mi-cuenta$/);
}

test('invitación y asignación de roles desde la pantalla de staff', async ({ page, browser, baseURL }) => {
  test.setTimeout(120_000);
  const adminEmail = `admin-${crypto.randomUUID()}@example.test`;
  const email = `staff-${crypto.randomUUID()}@example.test`;
  const env = { ...process.env, E2E_ADMIN_EMAIL: adminEmail, E2E_ADMIN_PASSWORD: password };
  const windows = platform() === 'win32';
  const fixture = fileURLToPath(new URL(windows ? './provision-fixture.ps1' : './provision-fixture.sh', import.meta.url));
  execFileSync(windows ? 'powershell.exe' : 'bash', windows ? ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', fixture] : [fixture],
    { env, timeout: 60_000, windowsHide: true, stdio: 'pipe' });
  await login(page, adminEmail);
  await page.getByRole('link', { name: 'Gestionar staff', exact: true }).click();
  await page.getByLabel('Correo del invitado').fill(email);
  await page.getByRole('button', { name: 'Enviar invitación' }).click();
  await expect(page.getByRole('status')).toContainText('se enviará un enlace');
  const directory = process.env.INVITATIONS_DIRECTORY ?? fileURLToPath(new URL('../../backend/.data/invitaciones/', import.meta.url));
  const message = readdirSync(directory).map(file => readFileSync(join(directory, file), 'utf8')).find(text => text.includes(`Para: ${email}\n`));
  if (!message) throw new Error('No se generó el correo local de la invitación');
  const token = message.match(/\/invitacion#([A-Za-z0-9_-]{43})/)?.[1];
  if (!token) throw new Error('Falta el enlace de aceptación');
  const guestContext = await browser.newContext({ baseURL });
  try {
    const guest = await guestContext.newPage();
    await guest.goto(`/invitacion#${token}`);
    await guest.getByLabel('Nombre', { exact: true }).fill('Invitado');
    await guest.getByLabel('Apellido').fill('Prueba');
    await guest.getByLabel('Contraseña', { exact: true }).fill(password);
    await guest.getByRole('button', { name: 'Aceptar invitación', exact: true }).click();
    await expect(guest.getByRole('status')).toContainText('debe asignarte un rol');
    await page.getByRole('button', { name: 'Actualizar miembros' }).click();
    const member = page.locator('.staff-member').filter({ hasText: email });
    await expect(member).toContainText('Sin rol');
    await member.getByLabel('Rol', { exact: true }).selectOption('RECLUTADOR');
    await member.getByRole('button', { name: 'Asignar rol' }).click();
    const verification = page.getByRole('dialog');
    await expect(verification).toBeVisible();
    await verification.getByRole('button', { name: 'Cancelar', exact: true }).click();
    await expect(member).toContainText('Sin rol');
    await member.getByRole('button', { name: 'Asignar rol' }).click();
    await verification.getByLabel('Tu contraseña de administrador').fill(password);
    await verification.getByRole('button', { name: 'Confirmar operación' }).click();
    await expect(page.getByRole('status')).toContainText('Rol actualizado');
    await login(guest, email);
    await expect(guest.getByRole('link', { name: 'Gestionar staff', exact: true })).toHaveCount(0);
    expect((await guest.request.get('/api/company/courses')).status()).toBe(403);
    await member.getByLabel('Rol', { exact: true }).selectOption('EDITOR');
    await member.getByRole('button', { name: 'Asignar rol' }).click();
    await verification.getByLabel('Tu contraseña de administrador').fill(password);
    await verification.getByRole('button', { name: 'Confirmar operación' }).click();
    await expect(member).toContainText('Activo · Editor de contenido');
    await guest.reload();
    await guest.getByRole('link', { name: 'Gestionar cursos y proyectos' }).click();
    expect((await guest.request.get('/api/company/courses')).status()).toBe(200);
    expect((await guest.request.get('/api/staff')).status()).toBe(403);
    await member.getByRole('button', { name: 'Dar de baja', exact: true }).click();
    await expect(verification.getByLabel('Tu contraseña de administrador')).toBeVisible();
    await verification.getByRole('button', { name: 'Cancelar', exact: true }).click();
    await expect(member).toContainText('Activo');
    expect((await guest.request.get('/api/account')).status()).toBe(200);
    await member.getByRole('button', { name: 'Dar de baja', exact: true }).click();
    await verification.getByLabel('Tu contraseña de administrador').fill(password);
    await verification.getByRole('button', { name: 'Confirmar operación' }).click();
    await expect(member).toContainText('Baja');
    expect((await guest.request.get('/api/account')).status()).toBe(403);
    expect((await guest.request.get('/api/account')).status()).toBe(401);
  } finally { await guestContext.close(); }
});
