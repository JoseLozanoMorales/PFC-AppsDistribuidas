import { expect, test } from '@playwright/test'
import { mockReadApis } from './fixtures.js'

test('un visitante consulta y filtra el catalogo', async ({ page }) => {
  await mockReadApis(page)
  await page.goto('./#/', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { name: 'Encuentra tu componente' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Procesador Ryzen 7' })).toBeVisible()
  await page.getByRole('searchbox', { name: 'Buscar producto' }).fill('producto inexistente')
  await expect(page.getByText('No encontramos productos')).toBeVisible()
})
