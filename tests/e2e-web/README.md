# Pruebas E2E web

Playwright recorre la aplicación React real en Chromium. Las respuestas HTTP se
interceptan con datos deterministas para probar navegación, autorización,
renderizado del catálogo y el panel de `AdminView` sin depender del estado de
CockroachDB ni de servicios externos.

```bash
cd Apps/web/frontend/webapp && npm ci
cd ../../../../tests/e2e-web && npm ci
npx playwright install chromium
npm test
```

Los reportes quedan en `playwright-report/` y las trazas/capturas de fallos en
`test-results/`.
