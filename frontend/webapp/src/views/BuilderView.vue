<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../services/api'
import { addToCart } from '../services/cart'
import { getUser } from '../services/session'

type Row = Record<string, unknown>
interface BuilderCategory { key: string; id: number; name: string; short: string; required: boolean }
interface BuilderProduct { id: number; name: string; price: number; image: string; brand: string }

const STORAGE_KEY = 'pcbuilder.selection'
const categories: BuilderCategory[] = [
  { key: 'cpu', id: 2, name: 'Procesador', short: 'CPU', required: true },
  { key: 'mobo', id: 8, name: 'Motherboard', short: 'MB', required: true },
  { key: 'ram', id: 7, name: 'Memoria RAM', short: 'RAM', required: true },
  { key: 'storage', id: 1, name: 'Almacenamiento', short: 'SSD', required: true },
  { key: 'gpu', id: 6, name: 'Tarjeta gráfica', short: 'GPU', required: false },
  { key: 'psu', id: 5, name: 'Fuente de poder', short: 'PSU', required: true },
  { key: 'case', id: 4, name: 'Gabinete', short: 'CASE', required: true },
  { key: 'cooling', id: 3, name: 'Refrigeración', short: 'COOL', required: false },
  { key: 'periferico', id: 9, name: 'Periféricos', short: 'I/O', required: false },
]

const router = useRouter()
const active = ref(categories[0])
const products = ref<BuilderProduct[]>([])
const selection = ref<Record<string, BuilderProduct>>({})
const loading = ref(false)
const adding = ref(false)
const error = ref('')
const notice = ref('')
const field = (row: Row, ...keys: string[]) => keys.map((key) => row[key]).find((value) => value !== undefined && value !== null)
const money = (value: number) => value.toLocaleString('es-EC', { style: 'currency', currency: 'USD' })
const total = computed(() => Object.values(selection.value).reduce((sum, product) => sum + product.price, 0))
const completed = computed(() => categories.filter((category) => category.required).every((category) => selection.value[category.key]))
const selectedCount = computed(() => Object.keys(selection.value).length)

function normalize(row: Row): BuilderProduct {
  const imageId = field(row, 'imagenId', 'imagen_id', 'portadaId', 'portada_id', 'galeriaId', 'galeria_id')
  return {
    id: Number(field(row, 'id', 'productoId', 'producto_id', 'id_producto') || 0),
    name: String(field(row, 'nombre', 'producto') || 'Producto'),
    price: Number(field(row, 'precio', 'preciounitario', 'precioUnitario', 'costo') || 0),
    image: imageId ? `/api/galeria_v2/img/${imageId}` : '/assets/placeholder.svg',
    brand: String(field(row, 'marca', 'nombre_marca') || ''),
  }
}

async function loadCategory(category: BuilderCategory) {
  active.value = category
  loading.value = true
  error.value = ''
  try {
    const rows = await api<Row[]>(`/api/productos/por-categoria?categoriaId=${category.id}`)
    products.value = rows.map(normalize).filter((product) => product.id > 0)
  } catch (cause) {
    products.value = []
    error.value = cause instanceof Error ? cause.message : 'No se pudieron cargar los componentes.'
  } finally { loading.value = false }
}

function select(product: BuilderProduct) {
  selection.value = { ...selection.value, [active.value.key]: product }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(selection.value))
  notice.value = `${product.name} añadido a la configuración.`
  window.setTimeout(() => { notice.value = '' }, 2200)
}

function remove(key: string) {
  const next = { ...selection.value }
  delete next[key]
  selection.value = next
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
}

function clearBuild() {
  selection.value = {}
  localStorage.removeItem(STORAGE_KEY)
}

async function addBuildToCart() {
  if (!getUser()) { await router.push({ path: '/login', query: { next: '/armado' } }); return }
  adding.value = true
  error.value = ''
  try {
    for (const product of Object.values(selection.value)) await addToCart(product.id, 1)
    notice.value = 'Configuración añadida al carrito.'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'No se pudo añadir la configuración.'
  } finally { adding.value = false }
}

onMounted(() => {
  try { selection.value = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}') || {} } catch { selection.value = {} }
  loadCategory(active.value)
})
</script>

<template>
  <section class="builder-page">
    <header class="builder-hero"><div><p class="eyebrow">PC Builder</p><h1>Arma el equipo<br /><em>perfecto para ti.</em></h1><p>Selecciona cada componente y guarda tu configuración en el carrito.</p></div><div class="build-progress"><strong>{{ selectedCount }}/{{ categories.length }}</strong><span>componentes elegidos</span><div><i :style="{ width: `${selectedCount / categories.length * 100}%` }"></i></div></div></header>

    <div class="builder-layout">
      <aside class="builder-slots">
        <div class="builder-title"><div><p class="eyebrow">Configuración</p><h2>Componentes</h2></div><button v-if="selectedCount" @click="clearBuild">Limpiar</button></div>
        <button v-for="category in categories" :key="category.key" class="builder-slot" :class="{ active: active.key === category.key, selected: selection[category.key] }" @click="loadCategory(category)">
          <span class="slot-icon">{{ category.short }}</span><span><strong>{{ category.name }}</strong><small>{{ selection[category.key]?.name || (category.required ? 'Requerido' : 'Opcional') }}</small></span><b v-if="selection[category.key]" @click.stop="remove(category.key)">×</b><i v-else>›</i>
        </button>
      </aside>

      <main class="builder-products">
        <div class="builder-products-head"><div><p class="eyebrow">Seleccionar</p><h2>{{ active.name }}</h2></div><span>{{ products.length }} opciones</span></div>
        <p v-if="loading" class="status">Buscando componentes…</p>
        <p v-else-if="error" class="alert">{{ error }}</p>
        <div v-else-if="products.length" class="builder-grid">
          <article v-for="product in products" :key="product.id" :class="{ chosen: selection[active.key]?.id === product.id }">
            <img :src="product.image" :alt="product.name" @error="($event.target as HTMLImageElement).src='/assets/placeholder.svg'" />
            <div><small>{{ product.brand || active.name }}</small><h3>{{ product.name }}</h3><strong>{{ money(product.price) }}</strong><div class="builder-card-actions"><RouterLink :to="`/producto/${product.id}`">Detalles</RouterLink><button @click="select(product)">{{ selection[active.key]?.id === product.id ? 'Seleccionado' : 'Elegir' }}</button></div></div>
          </article>
        </div>
        <div v-else class="builder-empty"><span>⌁</span><h3>No hay {{ active.name.toLowerCase() }} en CRDB</h3><p>La interfaz está lista; aparecerán aquí cuando se migre el catálogo distribuido.</p></div>
      </main>

      <aside class="build-summary">
        <p class="eyebrow">Resumen</p><h2>Tu equipo</h2>
        <div class="summary-lines"><p v-for="category in categories.filter(c => selection[c.key])" :key="category.key"><span>{{ category.short }}<small>{{ selection[category.key].name }}</small></span><strong>{{ money(selection[category.key].price) }}</strong></p></div>
        <p v-if="!selectedCount" class="summary-placeholder">Selecciona componentes para calcular tu configuración.</p>
        <div class="build-total"><span>Total estimado</span><strong>{{ money(total) }}</strong></div>
        <button class="button" :disabled="!selectedCount || adding" @click="addBuildToCart">{{ adding ? 'Añadiendo…' : 'Añadir al carrito' }}</button>
        <small :class="{ complete: completed }">{{ completed ? '✓ Configuración esencial completa' : 'Completa los componentes requeridos' }}</small>
      </aside>
    </div>
    <p v-if="notice" class="builder-toast">{{ notice }}</p>
  </section>
</template>
