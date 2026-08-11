<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../services/api'
import { addToCart } from '../services/cart'
import { getUser } from '../services/session'

type Row = Record<string, unknown>
const route = useRoute()
const router = useRouter()
const product = ref<Row | null>(null)
const gallery = ref<Row[]>([])
const activeImage = ref('')
const quantity = ref(1)
const loading = ref(true)
const adding = ref(false)
const error = ref('')
const notice = ref('')
const id = Number(route.params.id)
const field = (row: Row | null, ...keys: string[]) => keys.map((key) => row?.[key]).find((value) => value !== undefined && value !== null)
const name = computed(() => String(field(product.value, 'nombre', 'nombre_producto', 'producto') || 'Producto'))
const price = computed(() => Number(field(product.value, 'preciounitario', 'precioUnitario', 'precio', 'costo') || 0))
const description = computed(() => String(field(product.value, 'descripcion', 'detalle') || 'Componente disponible en TiendaTech.'))
const imageId = (row: Row | null) => field(row, 'imagenId', 'imagen_id', 'portadaId', 'portada_id', 'galeriaId', 'galeria_id', 'id')
const imageUrl = (row: Row | null) => imageId(row) ? `/api/galeria_v2/img/${imageId(row)}` : '/assets/placeholder.svg'

async function add() {
  notice.value = ''
  if (!getUser()) {
    await router.push({ path: '/login', query: { next: `/producto/${id}` } })
    return
  }
  adding.value = true
  try {
    await addToCart(id, quantity.value)
    notice.value = 'Producto añadido al carrito.'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'No se pudo añadir el producto.'
  } finally { adding.value = false }
}

onMounted(async () => {
  if (!Number.isInteger(id) || id <= 0) { error.value = 'Producto inválido.'; loading.value = false; return }
  try {
    product.value = await api<Row>(`/api/productos/${id}`).catch(async () => {
      const rows = await api<Row[]>('/api/productos?size=200')
      return rows.find((row) => Number(field(row, 'producto_id', 'productoId', 'id_producto', 'id')) === id) || null
    })
    if (!product.value) throw new Error('Producto no encontrado.')
    gallery.value = await api<Row[]>(`/api/galeria_v2/producto/${id}?scope=galeria`).catch(() => [])
    activeImage.value = gallery.value.length ? imageUrl(gallery.value[0]) : imageUrl(product.value)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'No se pudo cargar el producto.'
  } finally { loading.value = false }
})
</script>

<template>
  <section class="detail-wrap">
    <RouterLink class="back-link" to="/">← Volver al catálogo</RouterLink>
    <p v-if="loading" class="status">Cargando producto…</p>
    <p v-else-if="error && !product" class="alert">{{ error }}</p>
    <div v-else-if="product" class="detail-grid">
      <div class="gallery-panel">
        <img class="detail-image" :src="activeImage" :alt="name" @error="($event.target as HTMLImageElement).src='/assets/placeholder.svg'" />
        <div v-if="gallery.length > 1" class="thumbnails"><button v-for="(item, index) in gallery" :key="index" @click="activeImage = imageUrl(item)"><img :src="imageUrl(item)" alt="" /></button></div>
      </div>
      <div class="detail-info">
        <p class="eyebrow">{{ field(product, 'categoria', 'categoria_nombre', 'nombre_categoria') || 'Componente' }}</p>
        <h1>{{ name }}</h1>
        <p class="detail-description">{{ description }}</p>
        <strong class="detail-price">{{ price.toLocaleString('es-EC', { style: 'currency', currency: 'USD' }) }}</strong>
        <div class="buy-row"><label>Cantidad<input v-model.number="quantity" type="number" min="1" max="99" /></label><button class="button" :disabled="adding" @click="add">{{ adding ? 'Añadiendo…' : 'Añadir al carrito' }}</button></div>
        <p v-if="notice" class="success">{{ notice }} <RouterLink to="/carrito">Ver carrito</RouterLink></p>
        <p v-if="error" class="alert">{{ error }}</p>
        <ul class="benefits"><li>✓ Compra protegida</li><li>✓ Inventario actualizado</li><li>✓ Atención especializada</li></ul>
      </div>
    </div>
  </section>
</template>
