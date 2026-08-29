from locust import HttpUser, between, task


class TiendaTechUser(HttpUser):
    """Carga reproducible contra rutas públicas que atraviesan el API Gateway."""

    wait_time = between(0.5, 1.5)

    @task(5)
    def catalogo(self):
        with self.client.get("/api/productos?page=0&size=20", name="GET /api/productos", catch_response=True) as response:
            if response.status_code >= 500:
                response.failure(f"backend error: {response.status_code}")

    @task(2)
    def categorias(self):
        self.client.get("/api/categorias", name="GET /api/categorias")

    @task(2)
    def marcas(self):
        self.client.get("/api/marcas", name="GET /api/marcas")

    @task(1)
    def provincias(self):
        self.client.get("/api/provincias", name="GET /api/provincias")
