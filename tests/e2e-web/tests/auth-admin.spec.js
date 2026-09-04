import { expect, test } from '@playwright/test'
import { mockReadApis } from './fixtures.js'

test('un visitante no puede abrir administracion', async ({ page }) => {
  await page.goto('./#/admin', { waitUntil: 'domcontentloaded' })
  await expect(page).toHaveURL(/#\/login\?next=%2Fadmin/)
  await expect(page.getByRole('heading', { name: 'Inicia sesión' })).toBeVisible()
})

test('un administrador inicia sesion y gestiona productos', async ({ page }) => {
  await mockReadApis(page)
  await page.route('**/api/login', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      user: { usuarioId: 1, usuario: 'admin', nombre: 'Administrador E2E', idRol: 1 },
      access: 'mock',
    }),
  }))

  await page.goto('./#/login?next=%2Fadmin', { waitUntil: 'domcontentloaded' })
  await page.getByLabel('Usuario').fill('admin')
  await page.getByLabel('Contraseña').fill('Secreto123!')
  await page.getByRole('button', { name: 'Entrar' }).click()

  await expect(page).toHaveURL(/#\/admin$/)
  await expect(page.getByRole('heading', { name: 'Vista general' })).toBeVisible()
  await page.getByRole('button', { name: /Productos/ }).click()
  await expect(page.getByRole('heading', { name: 'Gestión de productos' })).toBeVisible()
  await expect(page.getByText('Procesador Ryzen 7')).toBeVisible()
  await page.getByRole('button', { name: 'Crear producto' }).click()
  await expect(page.getByRole('heading', { name: 'Crear producto' })).toBeVisible()
})
