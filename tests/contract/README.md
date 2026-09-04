# Pruebas de contrato Pact

Contratos *consumer-driven* de las interfaces HTTP consumidas por la web y la
aplicación móvil. Las pruebas generan contratos Pact V4 en `pacts/`.

```bash
cd tests/contract
npm ci
npm test
```

Los estados del proveedor documentan las precondiciones que deben implementar
los verificadores de `productos-service` y `usuarios-service`. CI conserva los
Pact generados como evidencia auditable.
