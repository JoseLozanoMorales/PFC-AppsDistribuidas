import { api, type PageResponse } from './api'
import { getUser } from './session'

export interface Cart { carritoId: number; usuarioId?: number; total?: number }
export interface CartLine { carritoId: number; productoId: number; cantidad: number; precioUnitario: number }
export interface ReservationResult { accepted: boolean; message: string; reservedQuantity: number; availableStock: number; lamportTimestamp: number; winningDeviceId: string; replayed: boolean }

const DEVICE_KEY = 'tiendatech-device-id'
const CLOCK_KEY = 'tiendatech-lamport'
function deviceId() { let id = sessionStorage.getItem(DEVICE_KEY); if (!id) { id = crypto.randomUUID(); sessionStorage.setItem(DEVICE_KEY, id) } return id }
function tick(remote = 0) { const local = Number(localStorage.getItem(CLOCK_KEY) || 0), next = Math.max(local, remote) + 1; localStorage.setItem(CLOCK_KEY, String(next)); return next }
function metadata() { return { deviceId: deviceId(), lamportTimestamp: tick(), operationId: crypto.randomUUID() } }
function observe(result: ReservationResult) { tick(result.lamportTimestamp); return result }

export async function currentCart(): Promise<Cart> {
  const user = getUser()
  const userId = user?.usuarioId ?? user?.id
  if (!userId) throw new Error('Debes iniciar sesión para usar el carrito.')
  return api<Cart>(`/api/carrito/${userId}`)
}

export async function cartLines(cartId: number): Promise<CartLine[]> {
  const page = await api<PageResponse<CartLine>>(`/api/carrito/${cartId}/detalle`)
  return page.content
}

export async function addToCart(productId: number, cantidad = 1): Promise<void> {
  const cart = await currentCart()
  observe(await api<ReservationResult>(`/api/carrito/${cart.carritoId}/agregar`, {
    method: 'POST',
    body: JSON.stringify({ productoId: productId, cantidad, ...metadata() }),
  }))
}

export async function updateCartLine(cartId: number, productId: number, cantidad: number): Promise<void> {
  observe(await api<ReservationResult>(`/api/carrito/${cartId}/actualizar/${productId}`, {
    method: 'PUT',
    body: JSON.stringify({ cantidad, ...metadata() }),
  }))
}

export async function removeCartLine(cartId: number, productId: number): Promise<void> {
  await updateCartLine(cartId, productId, 0)
}
