import assert from 'node:assert/strict'
import { test } from 'node:test'
import { MatchersV3, PactV4, SpecificationVersion } from '@pact-foundation/pact'

const { eachLike, integer, like, decimal } = MatchersV3

test('webapp obtiene el catalogo paginado', async () => {
  const pact = new PactV4({
    consumer: 'tiendatech-webapp',
    provider: 'productos-service',
    spec: SpecificationVersion.SPECIFICATION_VERSION_V4,
    dir: new URL('../pacts', import.meta.url).pathname,
  })

  await pact
    .addInteraction()
    .given('existen productos habilitados')
    .uponReceiving('una solicitud de la primera pagina del catalogo')
    .withRequest('GET', '/api/productos', builder => {
      builder.query({ page: '0', size: '12' })
    })
    .willRespondWith(200, builder => {
      builder.headers({ 'Content-Type': like('application/json') })
      builder.jsonBody(eachLike({
        producto_id: integer(1),
        nombre: like('Procesador Ryzen 7'),
        preciounitario: decimal(349.99),
        stock: integer(10),
        habilitado: like(true),
      }))
    })
    .executeTest(async mockServer => {
      const response = await fetch(`${mockServer.url}/api/productos?page=0&size=12`)
      assert.equal(response.status, 200)
      const products = await response.json()
      assert.ok(Array.isArray(products))
      assert.equal(typeof products[0].producto_id, 'number')
      assert.equal(typeof products[0].nombre, 'string')
    })
})
