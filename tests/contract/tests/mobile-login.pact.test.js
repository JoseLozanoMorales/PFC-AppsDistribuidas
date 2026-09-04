import assert from 'node:assert/strict'
import { test } from 'node:test'
import { MatchersV3, PactV4, SpecificationVersion } from '@pact-foundation/pact'

const { integer, like, regex } = MatchersV3

test('mobile inicia sesion con credenciales validas', async () => {
  const pact = new PactV4({
    consumer: 'tiendatech-mobile',
    provider: 'usuarios-service',
    spec: SpecificationVersion.SPECIFICATION_VERSION_V4,
    dir: new URL('../pacts', import.meta.url).pathname,
  })

  await pact
    .addInteraction()
    .given('el usuario cliente existe y esta habilitado')
    .uponReceiving('una solicitud de inicio de sesion valida')
    .withRequest('POST', '/api/login', builder => {
      builder.headers({ 'Content-Type': 'application/json' })
      builder.jsonBody({ usuario: like('cliente'), contrasena: like('Secreto123!') })
    })
    .willRespondWith(200, builder => {
      builder.headers({ 'Content-Type': like('application/json') })
      builder.jsonBody({
        user: {
          usuarioId: integer(20),
          usuario: like('cliente'),
          nombre: like('Cliente Pact'),
          idRol: integer(2),
        },
        access: regex('^[A-Za-z0-9._-]+$', 'pact-access-token'),
      })
    })
    .executeTest(async mockServer => {
      const response = await fetch(`${mockServer.url}/api/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ usuario: 'cliente', contrasena: 'Secreto123!' }),
      })
      assert.equal(response.status, 200)
      const session = await response.json()
      assert.equal(session.user.idRol, 2)
      assert.ok(session.access)
    })
})
