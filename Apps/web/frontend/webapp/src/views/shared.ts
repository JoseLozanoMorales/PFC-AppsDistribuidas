export type Row = Record<string, unknown>
export const field = (row: Row | null | undefined, ...keys: string[]) => keys.map((key) => row?.[key]).find((value) => value !== undefined && value !== null)
export const rowId = (row: Row, ...keys: string[]) => Number(field(row, ...keys) || 0)
export const money = (value: unknown) => Number(value || 0).toLocaleString('es-EC', { style: 'currency', currency: 'USD' })
export const productId = (row: Row) => rowId(row, 'producto_id', 'productoId', 'id_producto', 'id')
export const productName = (row?: Row) => String(field(row, 'nombre', 'nombre_producto', 'producto') || 'Producto')
export const productPrice = (row?: Row) => Number(field(row, 'preciounitario', 'precioUnitario', 'precio', 'costo') || 0)
export const productImage = (row?: Row) => {
  const id = field(row, 'imagenId', 'imagen_id', 'portadaId', 'portada_id', 'galeriaId', 'galeria_id')
  return id ? `/api/galeria_v2/img/${id}` : '/assets/placeholder.svg'
}
export const imageFallback = (event: React.SyntheticEvent<HTMLImageElement>) => { event.currentTarget.src = '/assets/placeholder.svg' }
