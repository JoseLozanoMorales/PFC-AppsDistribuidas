export const products = [{
  producto_id: 1,
  nombre: 'Procesador Ryzen 7',
  preciounitario: 349.99,
  descripcion: 'Procesador para pruebas E2E',
  stock: 10,
  habilitado: true,
}]

export async function mockReadApis(page) {
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url())
    let body = []
    if (url.pathname === '/api/productos') body = products
    else if (url.pathname === '/api/categorias') body = [{ id: 2, nombre: 'Procesadores' }]
    else if (url.pathname === '/api/marcas') body = [{ id: 1, nombre: 'AMD' }]
    else if (url.pathname === '/api/gamas') body = [{ id: 1, nombre: 'Alta' }]
    else if (url.pathname === '/api/sp/ivas') body = [{ iva_id: 1, porcentaje: 15 }]
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
  })
}
