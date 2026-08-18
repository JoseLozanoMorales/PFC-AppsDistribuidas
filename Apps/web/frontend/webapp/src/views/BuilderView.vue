<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../services/api'
import { addToCart } from '../services/cart'
import { getUser } from '../services/session'

type Row = Record<string, unknown>
interface BuilderCategory { key: string; id: number; name: string; short: string; required: boolean }
interface BuilderProduct { id: number; name: string; price: number; image: string; brand: string }

// --- Analisis de armado (armado-ia-service) ---
interface AnalisisComponente { id: number; nombre: string; precio: number }
interface AnalisisRecomendacion {
  presupuestoUsado: number
  componentes: Record<string, AnalisisComponente>
  porcentajeCuelloBotella: number | null
  nivelCuelloBotella: string | null
  componenteLimitante: string | null
  advertencias: string[]
}
interface AnalisisResultado {
  porcentajeCuelloBotella: number
  nivel: string
  componenteLimitante: string
  explicacion: string
  advertencias: string[]
  recomendacion: AnalisisRecomendacion | null
}

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

// --- Analisis de armado: estado propio, separado del armador manual ---
const budgetInput = ref('')
const analyzing = ref(false)
const analyzeError = ref('')
const analysisResult = ref<AnalisisResultado | null>(null)
const preAnalysisSelection = ref<Record<string, BuilderProduct> | null>(null)

const field = (row: Row, ...keys: string[]) => keys.map((key) => row[key]).find((value) => value !== undefined && value !== null)
const money = (value: number) => value.toLocaleString('es-EC', { style: 'currency', currency: 'USD' })
const total = computed(() => Object.values(selection.value).reduce((sum, product) => sum + product.price, 0))
const completed = computed(() => categories.filter((category) => category.required).every((category) => selection.value[category.key]))
const selectedCount = computed(() => Object.keys(selection.value).length)
const canAnalyze = computed(() => !!selection.value.cpu)
const canApplyRecommendation = computed(() =>
  !!analysisResult.value?.recomendacion && analysisResult.value.recomendacion.porcentajeCuelloBotella !== null,
)

const categoryName = (key: string) => categories.find((category) => category.key === key)?.name ?? key

function nivelClass(nivel: string | null | undefined) {
  if (nivel === 'SEVERO') return 'severo'
  if (nivel === 'MODERADO') return 'moderado'
  if (nivel === 'EQUILIBRADO') return 'equilibrado'
  return 'neutral'
}

// Invalida el analisis y el snapshot de "volver" cada vez que el usuario
// cambia el armado a mano: un analisis o una recomendacion mostrados sobre
// una seleccion que ya no existe serian enganosos. applyRecommendation() y
// restoreSelection() escriben `selection` directamente (no pasan por estas
// funciones), asi que no se autoinvalidan al aplicar o restaurar.
function invalidateAnalysis() {
  analysisResult.value = null
  preAnalysisSelection.value = null
}

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
  invalidateAnalysis()
}

function remove(key: string) {
  const next = { ...selection.value }
  delete next[key]
  selection.value = next
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  invalidateAnalysis()
}

function clearBuild() {
  selection.value = {}
  localStorage.removeItem(STORAGE_KEY)
  invalidateAnalysis()
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

// Normaliza el presupuesto de forma defensiva: no asume que budgetInput.value
// sea string (v-model sobre un input puede entregar tipos distintos segun el
// navegador/atributos), acepta coma decimal y espacios, y en vez de reventar
// devuelve un error explicito si el texto no es un numero valido.
function parsePresupuesto(raw: unknown): { value: number | null; error: string | null } {
  if (raw === null || raw === undefined) return { value: null, error: null }
  const texto = String(raw).trim()
  if (!texto) return { value: null, error: null }
  const normalizado = texto.replace(/\s+/g, '').replace(',', '.')
  const parsed = Number(normalizado)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return { value: null, error: 'El presupuesto debe ser un número positivo (ej. 1200 o 1200.50).' }
  }
  return { value: parsed, error: null }
}

async function runAnalysis() {
  if (!canAnalyze.value || analyzing.value) return
  const { value: presupuesto, error: presupuestoError } = parsePresupuesto(budgetInput.value)
  if (presupuestoError) {
    analyzeError.value = presupuestoError
    analysisResult.value = null
    return
  }

  analyzing.value = true
  analyzeError.value = ''
  analysisResult.value = null
  preAnalysisSelection.value = null
  try {
    const componentes = Object.fromEntries(
      categories.map((category) => [category.key, selection.value[category.key]?.id ?? null]),
    )
    const body: Record<string, unknown> = { componentes }
    if (presupuesto !== null) body.presupuestoMaximo = presupuesto
    analysisResult.value = await api<AnalisisResultado>('/api/armado/analizar', {
      method: 'POST',
      body: JSON.stringify(body),
    })
  } catch (cause) {
    analyzeError.value = cause instanceof Error ? cause.message : 'No se pudo analizar la configuración.'
  } finally {
    analyzing.value = false
  }
}

function applyRecommendation() {
  const recomendacion = analysisResult.value?.recomendacion
  if (!recomendacion || !canApplyRecommendation.value) return
  preAnalysisSelection.value = { ...selection.value }
  const nuevos: Record<string, BuilderProduct> = {}
  for (const [key, componente] of Object.entries(recomendacion.componentes)) {
    nuevos[key] = {
      id: componente.id,
      name: componente.nombre,
      price: componente.precio,
      image: '/assets/placeholder.svg',
      brand: '',
    }
  }
  selection.value = { ...selection.value, ...nuevos }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(selection.value))
  notice.value = 'Recomendación aplicada a tu configuración.'
  window.setTimeout(() => { notice.value = '' }, 2200)
}

function restoreSelection() {
  if (!preAnalysisSelection.value) return
  selection.value = { ...preAnalysisSelection.value }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(selection.value))
  preAnalysisSelection.value = null
  notice.value = 'Se restauró tu configuración anterior.'
  window.setTimeout(() => { notice.value = '' }, 2200)
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

    <section class="armado-analysis">
      <div class="armado-analysis-head">
        <div>
          <p class="eyebrow">Análisis con IA</p>
          <h2>¿Qué tan equilibrada está tu configuración?</h2>
          <p class="armado-hint">Es solo informativo: no bloquea la compra, podés seguir armando o añadir al carrito igual.</p>
        </div>
        <div class="armado-controls">
          <label>Presupuesto (opcional)
            <input type="text" inputmode="decimal" v-model="budgetInput" placeholder="Ej. 1200" />
          </label>
          <button class="button" :disabled="!canAnalyze || analyzing" @click="runAnalysis">
            {{ analyzing ? 'Analizando…' : 'Analizar mi configuración' }}
          </button>
        </div>
      </div>
      <p v-if="!canAnalyze" class="armado-hint">Elegí al menos un procesador para poder analizar.</p>
      <p v-if="analyzeError" class="alert">{{ analyzeError }}</p>

      <div v-if="analysisResult" class="armado-result">
        <div class="armado-score" :class="nivelClass(analysisResult.nivel)">
          <strong>{{ analysisResult.porcentajeCuelloBotella }}%</strong>
          <span>{{ analysisResult.nivel }}</span>
        </div>
        <div class="armado-detail">
          <p><strong>Componente limitante:</strong> {{ analysisResult.componenteLimitante }}</p>
          <p class="armado-explicacion">{{ analysisResult.explicacion }}</p>
          <ul v-if="analysisResult.advertencias.length" class="armado-warnings">
            <li v-for="(advertencia, index) in analysisResult.advertencias" :key="index">{{ advertencia }}</li>
          </ul>
        </div>

        <div v-if="analysisResult.recomendacion" class="armado-recommendation">
          <h3>Configuración recomendada</h3>
          <ul class="armado-rec-items">
            <li v-for="(componente, key) in analysisResult.recomendacion.componentes" :key="key">
              <span>{{ categoryName(String(key)) }}</span>
              <strong>{{ componente.nombre }}</strong>
              <small>{{ money(componente.precio) }}</small>
            </li>
          </ul>
          <div class="armado-rec-footer">
            <span>Total: <strong>{{ money(analysisResult.recomendacion.presupuestoUsado) }}</strong></span>
            <span v-if="analysisResult.recomendacion.porcentajeCuelloBotella !== null">
              Cuello de botella resultante:
              <strong :class="nivelClass(analysisResult.recomendacion.nivelCuelloBotella)">
                {{ analysisResult.recomendacion.porcentajeCuelloBotella }}%
              </strong>
            </span>
          </div>
          <ul v-if="analysisResult.recomendacion.advertencias.length" class="armado-warnings">
            <li v-for="(advertencia, index) in analysisResult.recomendacion.advertencias" :key="index">{{ advertencia }}</li>
          </ul>

          <div v-if="canApplyRecommendation" class="armado-rec-actions">
            <button class="button" @click="applyRecommendation">Aplicar esta recomendación</button>
            <button v-if="preAnalysisSelection" class="secondary-button" @click="restoreSelection">Volver a mi configuración</button>
          </div>
          <p v-else class="armado-hint armado-no-mejora">
            No se encontró una configuración de CPU/GPU que mejore tu rendimiento dentro de este presupuesto:
            se mantienen tu procesador y tarjeta gráfica actuales.
          </p>
        </div>
      </div>
    </section>

    <p v-if="notice" class="builder-toast">{{ notice }}</p>
  </section>
</template>
