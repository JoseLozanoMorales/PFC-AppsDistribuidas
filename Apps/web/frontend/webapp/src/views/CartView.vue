<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../services/api'
import { cartLines, currentCart, removeCartLine, updateCartLine, type CartLine } from '../services/cart'
import { getUser } from '../services/session'

type Row = Record<string, unknown>
type DisplayLine = CartLine & { product?: Row; busy?: boolean }
const router = useRouter()
const cartId = ref(0)
const lines = ref<DisplayLine[]>([])
const loading = ref(true)
const error = ref('')
const field = (row: Row | undefined, ...keys: string[]) => keys.map((key) => row?.[key]).find((value) => value !== undefined && value !== null)
const name = (line: DisplayLine) => String(field(line.product, 'nombre', 'nombre_producto', 'producto') || `Producto #${line.productoId}`)
const image = (line: DisplayLine) => { const imageId = field(line.product, 'imagenId', 'imagen_id', 'portadaId', 'portada_id', 'galeriaId', 'galeria_id'); return imageId ? `/api/galeria_v2/img/${imageId}` : '/assets/placeholder.svg' }
const subtotal = computed(() => lines.value.reduce((sum, line) => sum + Number(line.precioUnitario) * line.cantidad, 0))
const money = (value: number) => value.toLocaleString('es-EC', { style: 'currency', currency: 'USD' })

async function load() {
  if (!getUser()) { await router.push({ path: '/login', query: { next: '/carrito' } }); return }
  loading.value = true
  try {
    const cart = await currentCart()
    cartId.value = cart.carritoId
    const raw = await cartLines(cart.carritoId)
    const catalog = await api<Row[]>('/api/productos?size=200').catch(() => [])
    lines.value = await Promise.all(raw.map(async (line) => ({
      ...line,
      product: await api<Row>(`/api/productos/${line.productoId}`).catch(() =>
        catalog.find((row) => Number(field(row, 'producto_id', 'productoId', 'id_producto', 'id')) === line.productoId)),
    })))
  } catch (cause) { error.value = cause instanceof Error ? cause.message : 'No se pudo cargar el carrito.' }
  finally { loading.value = false }
}

async function change(line: DisplayLine, next: number) {
  const quantity = Math.max(1, Math.min(99, next || 1))
  line.busy = true
  try { await updateCartLine(cartId.value, line.productoId, quantity); line.cantidad = quantity }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'No se pudo actualizar la cantidad.' }
  finally { line.busy = false }
}

async function remove(line: DisplayLine) {
  line.busy = true
  try { await removeCartLine(cartId.value, line.productoId); lines.value = lines.value.filter((item) => item.productoId !== line.productoId) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'No se pudo quitar el producto.'; line.busy = false }
}

onMounted(load)
</script>

<template>
  <section class="cart-page">
    <div class="section-heading"><div><p class="eyebrow">Tu selección</p><h1>Carrito de compras</h1></div><RouterLink class="back-link" to="/">← Seguir comprando</RouterLink></div>
    <p v-if="loading" class="status">Cargando carrito…</p>
    <p v-else-if="error && !lines.length" class="alert">{{ error }}</p>
    <div v-else-if="lines.length" class="cart-layout">
      <div class="cart-list">
        <article v-for="line in lines" :key="line.productoId" class="cart-line" :class="{ busy: line.busy }">
          <img :src="image(line)" :alt="name(line)" @error="($event.target as HTMLImageElement).src='/assets/placeholder.svg'" />
          <div class="line-info"><RouterLink :to="`/producto/${line.productoId}`"><h2>{{ name(line) }}</h2></RouterLink><p>{{ money(Number(line.precioUnitario)) }} por unidad</p><button class="remove" @click="remove(line)">Quitar</button></div>
          <div class="quantity"><button @click="change(line, line.cantidad - 1)">−</button><input :value="line.cantidad" type="number" min="1" @change="change(line, Number(($event.target as HTMLInputElement).value))" /><button @click="change(line, line.cantidad + 1)">+</button></div>
          <strong>{{ money(Number(line.precioUnitario) * line.cantidad) }}</strong>
        </article>
      </div>
      <aside class="summary"><h2>Resumen</h2><p><span>Productos</span><strong>{{ lines.reduce((sum, line) => sum + line.cantidad, 0) }}</strong></p><p><span>Subtotal</span><strong>{{ money(subtotal) }}</strong></p><p><span>Envío</span><strong>Por calcular</strong></p><div class="summary-total"><span>Total</span><strong>{{ money(subtotal) }}</strong></div><RouterLink class="button" to="/pago">Continuar al pago</RouterLink></aside>
    </div>
    <div v-else class="empty-cart"><span>🛒</span><h2>Tu carrito está vacío</h2><p>Encuentra componentes para tu próximo equipo.</p><RouterLink class="button" to="/">Explorar productos</RouterLink></div>
    <p v-if="error && lines.length" class="alert">{{ error }}</p>
  </section>
</template>
