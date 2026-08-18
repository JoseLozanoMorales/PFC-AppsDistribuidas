import { api, type PageResponse } from './api'
import { getUser } from './session'

export interface Cart { carritoId: number; usuarioId?: number; total?: number }
export interface CartLine { carritoId: number; productoId: number; cantidad: number; precioUnitario: number }

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
  await api<void>(`/api/carrito/${cart.carritoId}/agregar`, {
    method: 'POST',
    body: JSON.stringify({ productoId: productId, cantidad }),
  })
}

export async function updateCartLine(cartId: number, productId: number, cantidad: number): Promise<void> {
  await api<void>(`/api/carrito/${cartId}/actualizar/${productId}`, {
    method: 'PUT',
    body: JSON.stringify({ cantidad }),
  })
}

export async function removeCartLine(cartId: number, productId: number): Promise<void> {
  await api<void>(`/api/carrito/${cartId}/quitar/${productId}`, { method: 'DELETE' })
}
