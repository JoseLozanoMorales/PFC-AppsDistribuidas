import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../services/api'
import { field, imageFallback, productId, productImage, productName, productPrice, Row, rowId, money } from './shared'

export default function CatalogView() {
  const [products, setProducts] = useState<Row[]>([]), [categories, setCategories] = useState<Row[]>([])
  const [selected, setSelected] = useState<number | null>(null), [query, setQuery] = useState(''), [loading, setLoading] = useState(true), [error, setError] = useState('')
  const loadProducts = async (category?: number) => { setLoading(true); setError(''); try { setProducts(await api<Row[]>(category ? `/api/productos/por-categoria?categoriaId=${category}` : '/api/productos?size=50')) } catch (cause) { setError(cause instanceof Error ? cause.message : 'No se pudo cargar el catálogo.') } finally { setLoading(false) } }
  useEffect(() => { void Promise.all([api<Row[]>('/api/categorias').then(setCategories).catch(() => setCategories([])), loadProducts()]) }, [])
  const visible = useMemo(() => products.filter((product) => productName(product).toLowerCase().includes(query.trim().toLowerCase()) && (selected === null || rowId(product, 'categoria_id', 'categoriaId', 'id_categoria', 'idCategoria') === selected)), [products, query, selected])
  const choose = (value: number | null) => { setSelected(value); void loadProducts(value || undefined) }
  return <><section className="hero"><div><p className="eyebrow">Tecnología a tu medida</p><h1>Todo para construir<br /><em>algo increíble.</em></h1><p>Explora componentes, compara opciones y arma el equipo ideal.</p></div><div className="hero-orbit" aria-hidden="true"><span>CPU</span><span>GPU</span><span>RAM</span><b>TT</b></div></section>
    <section className="catalog"><div className="section-heading"><div><p className="eyebrow">Catálogo</p><h2>Encuentra tu componente</h2></div><input value={query} onChange={(e) => setQuery(e.target.value)} className="search" type="search" placeholder="Buscar producto…" aria-label="Buscar producto" /></div>
      <div className="chips"><button className={selected === null ? 'active' : ''} onClick={() => choose(null)}>Todos</button>{categories.map((category) => { const id = rowId(category, 'id', 'id_categoria'); return <button key={id} className={selected === id ? 'active' : ''} onClick={() => choose(id)}>{String(field(category, 'nombre') || '')}</button> })}</div>
      {loading ? <p className="status">Cargando productos…</p> : error ? <p className="alert">{error}</p> : visible.length ? <div className="product-grid">{visible.map((product) => <article key={productId(product)} className="product-card"><img src={productImage(product)} alt={productName(product)} onError={imageFallback} /><div><p className="product-category">{String(field(product, 'categoria', 'categoria_nombre', 'nombre_categoria') || 'Componente')}</p><h3>{productName(product)}</h3><p className="product-description">{String(field(product, 'descripcion', 'detalle') || 'Disponible en TiendaTech')}</p><div className="product-footer"><strong>{money(productPrice(product))}</strong><Link to={`/producto/${productId(product)}`}>Ver detalle</Link></div></div></article>)}</div> : <p className="status">No encontramos productos con esos filtros.</p>}
    </section></>
}
