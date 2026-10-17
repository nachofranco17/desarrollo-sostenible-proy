import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { platform } from 'node:os';
import { fileURLToPath } from 'node:url';

const password = 'una frase de prueba segura';
const originalPdf = Buffer.from('%PDF-1.4\n% CV original RF6\n%%EOF\n');
const replacementPdf = Buffer.from('%PDF-1.7\n% CV actualizado RF6\n%%EOF\n');

function provisionCompany(email: string) {
  const windows = platform() === 'win32';
  const fixture = fileURLToPath(new URL(windows ? './provision-fixture.ps1' : './provision-fixture.sh', import.meta.url));
  execFileSync(windows ? 'powershell.exe' : 'bash',
    windows ? ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', fixture] : [fixture],
    { env: { ...process.env, E2E_ADMIN_EMAIL: email, E2E_ADMIN_PASSWORD: password },
      timeout: 60_000, windowsHide: true, stdio: 'pipe' });
}

async function login(page: Page, email: string) {
  await page.goto('/ingresar');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).toHaveURL(/\/mi-cuenta$/);
}

async function write(request: APIRequestContext, path: string, data: unknown, method = 'POST') {
  const csrfResponse = await request.get('/api/auth/csrf');
  expect(csrfResponse.ok()).toBeTruthy();
  const csrf = await csrfResponse.json();
  return request.fetch('/api' + path, { method, data, headers: { [csrf.headerName]: csrf.token } });
}

/** Solo prepara contenidos RF5; las operaciones de RF6 del recorrido usan la interfaz real. */
async function publishedRequirement(page: Page, tipo: 'CURSO' | 'PROYECTO', titulo: string) {
  const created = await write(page.request, '/company/courses', {
    tipo, titulo, descripcion: 'Contenido publicado para probar requisitos de ofertas.',
    tecnologia: 'Java', nivel: 'INICIAL', duracionHoras: 3, costo: 0,
  });
  expect(created.status()).toBe(201);
  const { id } = await created.json();
  const element = tipo === 'CURSO'
    ? await write(page.request, `/company/courses/${id}/lessons`, { titulo: 'Primera lección', cuerpo: 'Contenido de prueba.' })
    : await write(page.request, `/company/courses/${id}/deliverables`, {
      titulo: 'Primer entregable', consigna: 'Responder listo.', pista: 'Una palabra.', respuestasAceptadas: ['listo'],
    });
  expect(element.ok()).toBeTruthy();
  expect((await write(page.request, `/company/courses/${id}/publish`, {})).ok()).toBeTruthy();
  return id as string;
}

async function registerTalent(page: Page, email: string, name: string) {
  const registered = await write(page.request, '/auth/register', {
    nombre: name, apellido: 'Prueba', correo: email, password,
  });
  expect(registered.status()).toBe(202);
  await login(page, email);
}

async function uploadCv(page: Page, filename: string, content: Buffer) {
  await page.goto('/mi-curriculum');
  await expect(page.getByRole('heading', { name: 'Mi currículum', exact: true })).toBeVisible();
  await expect(page.getByText('Cargando…', { exact: true })).toHaveCount(0);
  await page.getByLabel('Archivo de currículum').setInputFiles({ name: filename, mimeType: 'application/pdf', buffer: content });
  await page.getByRole('button', { name: /^(Guardar|Reemplazar) currículum$/ }).click();
  await expect(page.getByText('Tu currículum quedó guardado.', { exact: true })).toBeVisible();
  await expect(page.getByText(filename, { exact: true })).toBeVisible();
}

test('RF6: publicar con requisitos, postularse con CV, filtrar perfiles y aislar empresas', async ({ page, browser, baseURL }) => {
  test.setTimeout(240_000);
  const suffix = crypto.randomUUID();
  const adminEmail = `rf6-admin-${suffix}@example.test`;
  const foreignEmail = `rf6-otra-${suffix}@example.test`;
  const talentEmail = `rf6-talento-${suffix}@example.test`;
  const secondEmail = `rf6-segundo-${suffix}@example.test`;
  provisionCompany(adminEmail);
  provisionCompany(foreignEmail);
  const otherContext = await browser.newContext({ baseURL });
  const talentContext = await browser.newContext({ baseURL });
  const secondContext = await browser.newContext({ baseURL });
  try {
    const other = await otherContext.newPage();
    const talent = await talentContext.newPage();
    const second = await secondContext.newPage();
    await login(page, adminEmail);
    await login(other, foreignEmail);
    const courseTitle = `Curso RF6 ${suffix}`;
    const projectTitle = `Proyecto RF6 ${suffix}`;
    const courseId = await publishedRequirement(page, 'CURSO', courseTitle);
    const projectId = await publishedRequirement(other, 'PROYECTO', projectTitle);
    const title = `Oferta RF6 ${suffix}`;

    await page.getByRole('link', { name: 'Gestionar ofertas laborales', exact: true }).click();
    await page.getByRole('link', { name: 'Crear oferta laboral', exact: true }).click();
    await page.getByLabel('Título de la oferta').fill(title);
    await page.getByLabel('Descripción de la oferta').fill('Buscamos un Talento para el equipo de desarrollo.');
    const requirementSearch = page.getByLabel('Buscar un curso o proyecto');
    await requirementSearch.fill(courseTitle);
    await page.getByRole('checkbox', { name: new RegExp(courseTitle) }).check();
    await requirementSearch.fill(projectTitle);
    await page.getByRole('checkbox', { name: new RegExp(projectTitle) }).check();
    await expect(page.getByText('2 requisitos seleccionados', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Guardar borrador', exact: true }).click();
    await expect(page).toHaveURL(/\/gestion-ofertas\/[0-9a-f-]{36}$/);
    const offerId = new URL(page.url()).pathname.split('/').at(-1)!;
    await expect(page.getByLabel('Título de la oferta')).toHaveValue(title);
    await expect(page.getByText('2 requisitos seleccionados', { exact: true })).toBeVisible();
    const draft = await (await page.request.get(`/api/company/offers/${offerId}`)).json();
    expect(draft.cursosRequeridos.sort()).toEqual([courseId, projectId].sort());
    await page.getByLabel('Descripción de la oferta').fill('Oferta revisada y lista para recibir postulaciones.');
    await page.getByRole('button', { name: 'Publicar oferta', exact: true }).click();
    await expect(page.getByText('Oferta publicada.', { exact: true })).toBeVisible();
    await expect(page.getByText('Publicada', { exact: true })).toBeVisible();

    await registerTalent(talent, talentEmail, 'Ana');
    expect((await write(talent.request, '/profile/me', { especializaciones: ['Backend'] }, 'PATCH')).ok()).toBeTruthy();
    await talent.getByRole('link', { name: 'Ver ofertas laborales', exact: true }).click();
    await talent.locator('article').filter({ hasText: title }).getByRole('link', { name: 'Ver oferta', exact: true }).click();
    await expect(talent.getByRole('button', { name: 'Postularme', exact: true })).toBeDisabled();
    await expect(talent.getByRole('link', { name: courseTitle, exact: true })).toBeVisible();
    await expect(talent.getByRole('link', { name: projectTitle, exact: true })).toBeVisible();
    await uploadCv(talent, 'cv-original.pdf', originalPdf);
    await talent.goto(`/ofertas/${offerId}`);
    await talent.getByRole('button', { name: 'Postularme', exact: true }).click();
    await expect(talent.getByText('Tu postulación quedó registrada.', { exact: true })).toBeVisible();
    await talent.getByRole('link', { name: 'Ver mis postulaciones', exact: true }).click();
    await expect(talent.getByRole('row').filter({ hasText: title })).toContainText('Enviada');
    await talent.goto(`/ofertas/${offerId}`);
    await expect(talent.getByText('Ya te postulaste a esta oferta.', { exact: true })).toBeVisible();
    expect((await write(talent.request, `/offers/${offerId}/applications`, {})).status()).toBe(409);
    await uploadCv(talent, 'cv-reemplazado.pdf', replacementPdf);

    await registerTalent(second, secondEmail, 'Bruno');
    expect((await write(second.request, '/profile/me', { especializaciones: ['Frontend'] }, 'PATCH')).ok()).toBeTruthy();
    await uploadCv(second, 'cv-segundo.pdf', originalPdf);
    await second.goto(`/ofertas/${offerId}`);
    await second.getByRole('button', { name: 'Postularme', exact: true }).click();
    await expect(second.getByText('Tu postulación quedó registrada.', { exact: true })).toBeVisible();

    await page.getByRole('link', { name: 'Ver postulantes', exact: true }).click();
    const ana = page.getByRole('row').filter({ hasText: talentEmail });
    const bruno = page.getByRole('row').filter({ hasText: secondEmail });
    await expect(ana).toBeVisible();
    await expect(bruno).toBeVisible();
    const list = await (await page.request.get(`/api/company/offers/${offerId}/applications`)).json();
    const applicationId = list.find((item: { perfil: { correo: string } }) => item.perfil.correo === talentEmail).id;
    const today = new Date().toISOString().slice(0, 10);
    await page.getByLabel('Nombre o apellido').fill('Ana');
    await page.getByLabel('Especialización', { exact: true }).fill('Backend');
    await page.getByLabel('Fecha desde').fill(today);
    await page.getByLabel('Fecha hasta').fill(today);
    await page.getByRole('button', { name: 'Aplicar filtros', exact: true }).click();
    await expect(ana).toBeVisible();
    await expect(bruno).toHaveCount(0);
    await ana.getByRole('link', { name: 'Ver perfil', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Perfil del postulante', exact: true })).toBeVisible();
    await expect(page.locator('dd').filter({ hasText: talentEmail })).toBeVisible();
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('link', { name: 'Descargar CV de esta postulación', exact: true }).click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toBe('cv-original.pdf');
    expect(readFileSync((await download.path())!)).toEqual(originalPdf);
    await page.getByRole('link', { name: /Volver a postulantes/ }).click();
    await expect(page.getByLabel('Especialización', { exact: true })).toHaveValue('Backend');
    await page.getByLabel('Nombre o apellido').fill('Sin coincidencias');
    await page.getByRole('button', { name: 'Aplicar filtros', exact: true }).click();
    await expect(page.getByRole('status')).toContainText('No hay postulaciones que coincidan');
    await page.getByRole('button', { name: 'Limpiar filtros', exact: true }).click();
    await expect(bruno).toBeVisible();

    const foreignBase = `/api/company/offers/${offerId}/applications`;
    for (const resource of [foreignBase, `${foreignBase}/${applicationId}/profile`, `${foreignBase}/${applicationId}/curriculum`]) {
      const response = await other.request.get(resource);
      expect(response.status()).toBe(403);
      expect(await response.text()).not.toContain(talentEmail);
    }
    await other.goto(`/gestion-ofertas/${offerId}/postulantes`);
    await expect(other.getByRole('alert')).toBeVisible();
    await expect(other.getByRole('row').filter({ hasText: talentEmail })).toHaveCount(0);
    await talent.setViewportSize({ width: 375, height: 812 });
    await talent.goto(`/ofertas/${offerId}`);
    await expect(talent.getByRole('heading', { name: title, exact: true })).toBeVisible();
    expect(await talent.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  } finally {
    await otherContext.close();
    await talentContext.close();
    await secondContext.close();
  }
});
