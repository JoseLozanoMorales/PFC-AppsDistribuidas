<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../services/api'

type Row = Record<string, unknown>
const products = ref<Row[]>([])
const categories = ref<Row[]>([])
const selected = ref<number | null>(null)
const query = ref('')
const loading = ref(true)
const error = ref('')

const field = (row: Row, ...keys: string[]) => keys.map((key) => row[key]).find((value) => value !== undefined && value !== null)
const productId = (row: Row) => Number(field(row, 'producto_id', 'productoId', 'id_producto', 'id') || 0)
const categoryId = (row: Row) => Number(field(row, 'categoria_id', 'categoriaId', 'id_categoria', 'idCategoria') || 0)
const name = (row: Row) => String(field(row, 'nombre', 'nombre_producto', 'producto') || 'Producto')
const price = (row: Row) => Number(field(row, 'preciounitario', 'precioUnitario', 'precio', 'costo') || 0)
const imageId = (row: Row) => field(row, 'imagenId', 'imagen_id', 'portadaId', 'portada_id', 'galeriaId', 'galeria_id')
const image = (row: Row) => imageId(row) ? `/api/galeria_v2/img/${imageId(row)}` : '/assets/placeholder.svg'

const visible = computed(() => products.value.filter((product) => {
  const matchesText = name(product).toLocaleLowerCase().includes(query.value.trim().toLocaleLowerCase())
  const matchesCategory = selected.value === null || categoryId(product) === selected.value
  return matchesText && matchesCategory
}))

async function loadProducts(category?: number) {
  loading.value = true
  error.value = ''
  try {
    products.value = await api<Row[]>(category ? `/api/productos/por-categoria?categoriaId=${category}` : '/api/productos?size=50')
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'No se pudo cargar el catálogo.'
  } finally { loading.value = false }
}

function chooseCategory(value: number | null) {
  selected.value = value
  loadProducts(value || undefined)
}

onMounted(async () => {
  const categoryRequest = api<Row[]>('/api/categorias').then((rows) => { categories.value = rows }).catch(() => { categories.value = [] })
  await Promise.all([categoryRequest, loadProducts()])
})
</script>

<template>
  <section class="hero">
    <div><p class="eyebrow">Tecnología a tu medida</p><h1>Todo para construir<br /><em>algo increíble.</em></h1><p>Explora componentes, compara opciones y arma el equipo ideal.</p></div>
    <div class="hero-orbit" aria-hidden="true"><span>CPU</span><span>GPU</span><span>RAM</span><b>TT</b></div>
  </section>
  <section class="catalog">
    <div class="section-heading"><div><p class="eyebrow">Catálogo</p><h2>Encuentra tu componente</h2></div><input v-model="query" class="search" type="search" placeholder="Buscar producto…" aria-label="Buscar producto" /></div>
    <div class="chips"><button :class="{ active: selected === null }" @click="chooseCategory(null)">Todos</button><button v-for="category in categories" :key="Number(field(category, 'id', 'id_categoria'))" :class="{ active: selected === Number(field(category, 'id', 'id_categoria')) }" @click="chooseCategory(Number(field(category, 'id', 'id_categoria')))">{{ field(category, 'nombre') }}</button></div>
    <p v-if="loading" class="status">Cargando productos…</p>
    <p v-else-if="error" class="alert">{{ error }}</p>
    <div v-else-if="visible.length" class="product-grid">
      <article v-for="product in visible" :key="productId(product)" class="product-card">
        <img :src="image(product)" :alt="name(product)" @error="($event.target as HTMLImageElement).src='/assets/placeholder.svg'" />
        <div><p class="product-category">{{ field(product, 'categoria', 'categoria_nombre', 'nombre_categoria') || 'Componente' }}</p><h3>{{ name(product) }}</h3><p class="product-description">{{ field(product, 'descripcion', 'detalle') || 'Disponible en TiendaTech' }}</p><div class="product-footer"><strong>{{ price(product).toLocaleString('es-EC', { style: 'currency', currency: 'USD' }) }}</strong><RouterLink :to="`/producto/${productId(product)}`">Ver detalle</RouterLink></div></div>
      </article>
    </div>
    <p v-else class="status">No encontramos productos con esos filtros.</p>
  </section>
</template>
