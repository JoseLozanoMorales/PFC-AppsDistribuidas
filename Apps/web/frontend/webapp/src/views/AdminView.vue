<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../services/api'
import { getUser } from '../services/session'

type Row = Record<string, unknown>
type Section = 'resumen' | 'usuarios' | 'productos' | 'sistema'

const section = ref<Section>('resumen')
const currentUser = getUser()
const products = ref<Row[]>([])
const categories = ref<Row[]>([])
const brands = ref<Row[]>([])
const ranges = ref<Row[]>([])
const taxes = ref<Row[]>([])
const users = reactive<Record<number, Row[]>>({ 1: [], 2: [], 3: [] })
const userRole = ref(1)
const userQuery = ref('')
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const showUserForm = ref(false)
const showProductForm = ref(false)
const editingUserId = ref<number | null>(null)
const editingProductId = ref<number | null>(null)
const productImages = ref<File[]>([])
const existingGallery = ref<Row[]>([])
const galleryOrderDirty = ref(false)
const draggedImageIndex = ref<number | null>(null)
const imageDropIndex = ref<number | null>(null)

const userForm = reactive({ nombre: '', cedula: '', correo: '', telefono: '', usuario: '', contrasena: '', idRol: 2 })
const productForm = reactive({ nombre: '', categoriaId: 0, marcaId: 0, gamaId: 1, ivaId: 1, precio: 0, costo: 0, stock: 0, enlace: '', atributos: '{}' })
const productAttributes = reactive<Record<string, string | number>>({})
const categoryFields: Record<number, { name: string; label: string; type: 'text' | 'number'; placeholder?: string }[]> = {
  1: [{ name: 'capacidad', label: 'Capacidad (GB)', type: 'number', placeholder: 'Ej. 1000' }, { name: 'tipo', label: 'Tipo de almacenamiento', type: 'text', placeholder: 'NVMe, SATA, HDD…' }],
  2: [{ name: 'sockets', label: 'Socket', type: 'text', placeholder: 'Ej. AM5 o LGA1700' }, { name: 'generacion', label: 'Generación', type: 'number', placeholder: 'Ej. 14' }],
  3: [{ name: 'tamanio', label: 'Tamaño del cooler (mm)', type: 'number' }, { name: 'socket', label: 'Socket compatible', type: 'text' }],
  4: [{ name: 'tamanio_gpu', label: 'Longitud máxima de GPU (mm)', type: 'number' }, { name: 'tamanio_refrigeracion', label: 'Tamaño de refrigeración (mm)', type: 'number' }],
  5: [{ name: 'consumo_energia', label: 'Potencia (W)', type: 'number', placeholder: 'Ej. 750' }],
  6: [{ name: 'tamanio', label: 'Longitud de la GPU (mm)', type: 'number' }, { name: 'consumo_energia', label: 'Consumo energético (W)', type: 'number' }],
  7: [{ name: 'velocidades', label: 'Velocidad (MHz)', type: 'number', placeholder: 'Ej. 6000' }],
  8: [{ name: 'socket', label: 'Socket', type: 'text' }, { name: 'velocidad_ram', label: 'Velocidad máxima de RAM (MHz)', type: 'number' }, { name: 'chipset', label: 'Chipset', type: 'text' }],
  9: [{ name: 'tipo', label: 'Tipo de periférico', type: 'text', placeholder: 'Teclado, ratón, monitor…' }],
  10: [],
}
const selectedCategoryFields = computed(() => categoryFields[Number(productForm.categoriaId)] || [])

const field = (row: Row, ...keys: string[]) => keys.map((key) => row[key]).find((value) => value !== undefined && value !== null)
const rowId = (row: Row, ...keys: string[]) => Number(field(row, ...keys) || 0)
const activeUsers = computed(() => users[userRole.value] || [])
const filteredUsers = computed(() => {
  const query = userQuery.value.trim().toLowerCase()
  return activeUsers.value.filter((row) => !query || ['usuario', 'nombre', 'correo', 'cedula'].some((key) => String(row[key] || '').toLowerCase().includes(query)))
})

async function safeList(path: string): Promise<Row[]> {
  try { const data = await api<Row[]>(path); return Array.isArray(data) ? data : [] } catch { return [] }
}

async function load() {
  loading.value = true
  error.value = ''
  const [productRows, categoryRows, brandRows, rangeRows, taxRows, admins, clients, workers] = await Promise.all([
    safeList('/api/productos?size=200'), safeList('/api/categorias'), safeList('/api/marcas'), safeList('/api/gamas'), safeList('/api/sp/ivas'),
    safeList('/api/usuarios/buscar-min?q=&rolId=1&limit=200'), safeList('/api/usuarios/buscar-min?q=&rolId=2&limit=200'), safeList('/api/usuarios/buscar-min?q=&rolId=3&limit=200'),
  ])
  products.value = productRows; categories.value = categoryRows; brands.value = brandRows; ranges.value = rangeRows; taxes.value = taxRows
  users[1] = admins; users[2] = clients; users[3] = workers
  loading.value = false
}

function clearMessages() { error.value = ''; notice.value = '' }
function resetUserForm() {
  Object.assign(userForm, { nombre: '', cedula: '', correo: '', telefono: '', usuario: '', contrasena: '', idRol: userRole.value })
  editingUserId.value = null
}
function openNewUser() { clearMessages(); resetUserForm(); showUserForm.value = true }
function openEditUser(row: Row) {
  clearMessages(); editingUserId.value = rowId(row, 'usuarioId', 'usuario_id', 'id')
  Object.assign(userForm, { nombre: field(row, 'nombre') || '', cedula: field(row, 'cedula') || '', correo: field(row, 'correo') || '', telefono: field(row, 'telefono') || '', usuario: field(row, 'usuario') || '', contrasena: '', idRol: userRole.value })
  showUserForm.value = true
}
async function saveUser() {
  clearMessages(); saving.value = true
  try {
    const body = { ...userForm, idRol: Number(userForm.idRol), idMetodoPago: null }
    if (editingUserId.value) {
      const path = userForm.idRol === 2 ? `/api/usuarios/cliente/${editingUserId.value}` : `/api/usuarios/admin/${editingUserId.value}?rolId=${userForm.idRol}`
      await api(path, { method: 'PUT', body: JSON.stringify(body) })
    } else {
      const path = userForm.idRol === 2 ? '/api/usuarios/crear' : '/api/usuarios/crear-usuarioAdmin'
      await api(path, { method: 'POST', body: JSON.stringify(body) })
    }
    notice.value = editingUserId.value ? 'Usuario actualizado.' : 'Usuario creado correctamente.'
    showUserForm.value = false; await load()
  } catch (cause) { error.value = cause instanceof Error ? cause.message : 'No se pudo guardar el usuario.' }
  finally { saving.value = false }
}
async function disableUser(row: Row) {
  if (!confirm(`¿Deshabilitar a ${field(row, 'usuario') || 'este usuario'}?`)) return
  clearMessages()
  try { await api(`/api/usuarios/admin/${rowId(row, 'usuarioId', 'usuario_id', 'id')}?rolId=${userRole.value}`, { method: 'DELETE' }); notice.value = 'Usuario deshabilitado.'; await load() }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'No se pudo deshabilitar.' }
}

function resetProductForm() {
  Object.assign(productForm, { nombre: '', categoriaId: Number(field(categories.value[0] || {}, 'id', 'id_categoria') || 0), marcaId: Number(field(brands.value[0] || {}, 'id', 'marca_id') || 0), gamaId: Number(field(ranges.value[0] || {}, 'id', 'gama_id') || 1), ivaId: Number(field(taxes.value[0] || {}, 'iva_id', 'id') || 1), precio: 0, costo: 0, stock: 0, enlace: '', atributos: '{}' })
  editingProductId.value = null
  productImages.value = []; existingGallery.value = []
  galleryOrderDirty.value = false
  Object.keys(productAttributes).forEach((key) => delete productAttributes[key])
}
function changeCategory() { Object.keys(productAttributes).forEach((key) => delete productAttributes[key]) }
function openNewProduct() { clearMessages(); resetProductForm(); showProductForm.value = true }
async function openEditProduct(row: Row) {
  clearMessages(); saving.value = true
  try {
    const id = rowId(row, 'producto_id', 'productoId', 'id')
    const detail = await api<Row>(`/api/sp/productos/${id}/editar`)
    editingProductId.value = id
    existingGallery.value = await safeList(`/api/galeria_v2/producto/${id}`)
    galleryOrderDirty.value = false
    const attrs = field(detail, 'atributos')
    Object.assign(productForm, { nombre: field(detail, 'nombre') || '', categoriaId: Number(field(detail, 'categoria_id') || 0), marcaId: Number(field(detail, 'marca_id') || 0), gamaId: Number(field(detail, 'gama_id') || 1), ivaId: Number(field(detail, 'iva_id') || 1), precio: Number(field(detail, 'preciounitario', 'precio') || 0), costo: Number(field(detail, 'costo') || 0), stock: Number(field(detail, 'stock') || 0), enlace: field(detail, 'enlace') || '' })
    const parsed = typeof attrs === 'string' ? JSON.parse(attrs || '{}') : (attrs || {})
    Object.keys(productAttributes).forEach((key) => delete productAttributes[key]); Object.assign(productAttributes, parsed)
    showProductForm.value = true
  } catch (cause) { error.value = cause instanceof Error ? cause.message : 'No se pudo cargar el producto.' }
  finally { saving.value = false }
}
function selectImages(event: Event) { productImages.value = Array.from((event.target as HTMLInputElement).files || []) }
async function removeImage(row: Row) {
  const id = rowId(row, 'galeriaId', 'galeria_id', 'id')
  if (!confirm('¿Quitar esta imagen del producto?')) return
  await api(`/api/galeria_v2/${id}`, { method: 'DELETE' })
  existingGallery.value = existingGallery.value.filter((item) => rowId(item, 'galeriaId', 'galeria_id', 'id') !== id)
  galleryOrderDirty.value = true
}
function moveImage(index: number, direction: number) {
  const target = index + direction
  if (target < 0 || target >= existingGallery.value.length) return
  const reordered = [...existingGallery.value]
  ;[reordered[index], reordered[target]] = [reordered[target], reordered[index]]
  existingGallery.value = reordered; galleryOrderDirty.value = true
}
function startImageDrag(index: number, event: DragEvent) {
  draggedImageIndex.value = index; imageDropIndex.value = index
  if (event.dataTransfer) { event.dataTransfer.effectAllowed = 'move'; event.dataTransfer.setData('text/plain', String(index)) }
}
function enterImageDrop(index: number) { imageDropIndex.value = index }
function dropImage(index: number) {
  const from = draggedImageIndex.value
  if (from === null || from === index) { endImageDrag(); return }
  const reordered = [...existingGallery.value]
  const [moved] = reordered.splice(from, 1)
  reordered.splice(index, 0, moved)
  existingGallery.value = reordered; galleryOrderDirty.value = true; endImageDrag()
}
function endImageDrag() { draggedImageIndex.value = null; imageDropIndex.value = null }
async function saveImageOrder() {
  if (!editingProductId.value) return
  saving.value = true; clearMessages()
  try {
    const ids = existingGallery.value.map((row) => rowId(row, 'galeriaId', 'galeria_id', 'id'))
    await api(`/api/productos/${editingProductId.value}/galeria/orden`, { method: 'PATCH', body: JSON.stringify({ ids }) })
    galleryOrderDirty.value = false; notice.value = 'Orden de imágenes guardado. La primera imagen es la portada.'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : 'No se pudo guardar el orden.' }
  finally { saving.value = false }
}
async function saveProduct() {
  clearMessages(); saving.value = true
  try {
    const attributes: Row = Object.fromEntries(Object.entries(productAttributes).filter(([, value]) => value !== '' && value !== null))
    const body = { ...attributes, nombre: productForm.nombre, preciounitario: Number(productForm.precio), costo: Number(productForm.costo), stock: Number(productForm.stock), enlace: productForm.enlace || null, marca_id: Number(productForm.marcaId), gama_id: Number(productForm.gamaId), iva_id: Number(productForm.ivaId), categoria_id: Number(productForm.categoriaId) }
    let productId = editingProductId.value
    if (productId) await api(`/api/sp/productos/${productId}/basico`, { method: 'PUT', body: JSON.stringify(body) })
    else { const created = await api<Row>('/api/productos', { method: 'POST', body: JSON.stringify(body) }); productId = Number(field(created, 'productoId', 'producto_id')) }
    for (let index = 0; index < productImages.value.length; index++) {
      const data = new FormData(); data.append('file', productImages.value[index]); data.append('portada', String(index === 0 && existingGallery.value.length === 0))
      await api(`/api/productos/${productId}/galeria`, { method: 'POST', body: data })
    }
    notice.value = editingProductId.value ? 'Producto actualizado.' : 'Producto creado correctamente.'
    showProductForm.value = false; await load()
  } catch (cause) { error.value = cause instanceof Error ? cause.message : 'No se pudo guardar el producto.' }
  finally { saving.value = false }
}
async function disableProduct(row: Row) {
  if (!confirm(`¿Deshabilitar ${field(row, 'nombre') || 'este producto'}?`)) return
  clearMessages()
  try { await api(`/api/sp/productos/${rowId(row, 'producto_id', 'productoId', 'id')}`, { method: 'DELETE' }); notice.value = 'Producto deshabilitado.'; await load() }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'No se pudo deshabilitar.' }
}

onMounted(load)
</script>

<template>
  <section class="admin-shell">
    <aside class="admin-sidebar">
      <div><p class="eyebrow">Administración</p><h1>TiendaTech</h1><p class="admin-user">{{ currentUser?.nombre }}<small>{{ currentUser?.usuario }}</small></p></div>
      <nav><button :class="{active:section==='resumen'}" @click="section='resumen'">◈ Resumen</button><button :class="{active:section==='usuarios'}" @click="section='usuarios'">♙ Usuarios</button><button :class="{active:section==='productos'}" @click="section='productos'">◇ Productos</button><button :class="{active:section==='sistema'}" @click="section='sistema'">◎ Sistema CRDB</button></nav>
      <RouterLink class="legacy-link" to="/">Volver a la tienda</RouterLink>
    </aside>
    <div class="admin-content">
      <header class="admin-heading"><div><p class="eyebrow">Panel administrativo</p><h2>{{ section==='resumen'?'Vista general':section==='usuarios'?'Gestión de usuarios':section==='productos'?'Gestión de productos':'Entorno distribuido' }}</h2></div><button class="refresh" :disabled="loading" @click="load">↻ Actualizar</button></header>
      <p v-if="error" class="alert">{{ error }}</p><p v-if="notice" class="admin-success">{{ notice }}</p><p v-if="loading" class="status">Consultando servicios CRDB…</p>

      <template v-else-if="section==='resumen'">
        <div class="metric-grid"><article><span>Productos</span><strong>{{products.length}}</strong><small>Catálogo creado en CRDB</small></article><article><span>Categorías</span><strong>{{categories.length}}</strong><small>Catálogos de referencia</small></article><article><span>Administradores</span><strong>{{users[1].length}}</strong><small>Cuentas activas</small></article><article><span>Trabajadores</span><strong>{{users[3].length}}</strong><small>Cuentas activas</small></article></div>
        <div class="admin-card notice-card"><div><p class="eyebrow">Gestión operativa</p><h3>Usuarios y productos habilitados</h3><p>Los registros se crean directamente en CockroachDB. No se copiaron usuarios ni productos del entorno anterior.</p></div><span class="state-pill ok">Disponible</span></div>
      </template>

      <template v-else-if="section==='usuarios'">
        <div class="admin-toolbar"><div class="segmented"><button v-for="o in [{id:1,label:'Administradores'},{id:3,label:'Trabajadores'},{id:2,label:'Clientes'}]" :key="o.id" :class="{active:userRole===o.id}" @click="userRole=o.id">{{o.label}}</button></div><div class="toolbar-actions"><input v-model="userQuery" class="search" type="search" placeholder="Buscar usuario…"><button class="button small" @click="openNewUser">+ Crear usuario</button></div></div>
        <form v-if="showUserForm" class="admin-editor" @submit.prevent="saveUser"><header><div><p class="eyebrow">{{editingUserId?'Editar':'Nueva'}} cuenta</p><h3>{{editingUserId?'Actualizar usuario':'Crear usuario'}}</h3></div><button type="button" class="icon-close" @click="showUserForm=false">×</button></header><div class="admin-form-grid"><label>Nombre completo<input v-model="userForm.nombre" required></label><label>Cédula<input v-model="userForm.cedula" required></label><label>Correo<input v-model="userForm.correo" type="email" required></label><label>Teléfono<input v-model="userForm.telefono" required></label><label>Usuario<input v-model="userForm.usuario" required></label><label>Rol<select v-model.number="userForm.idRol" :disabled="!!editingUserId"><option :value="1">Administrador</option><option :value="3">Trabajador</option><option :value="2">Cliente</option></select></label><label class="wide">{{editingUserId?'Nueva contraseña (opcional)':'Contraseña'}}<input v-model="userForm.contrasena" type="password" minlength="8" :required="!editingUserId"></label></div><div class="editor-actions"><button type="button" class="secondary-button" @click="showUserForm=false">Cancelar</button><button class="button small" :disabled="saving">{{saving?'Guardando…':'Guardar usuario'}}</button></div></form>
        <div class="admin-table-wrap"><table><thead><tr><th>Usuario</th><th>Nombre</th><th>Correo</th><th>Rol</th><th>Estado</th><th>Acciones</th></tr></thead><tbody><tr v-for="row in filteredUsers" :key="rowId(row,'usuarioId','usuario_id','id')"><td><strong>{{field(row,'usuario')||'—'}}</strong></td><td>{{field(row,'nombre')||'—'}}</td><td>{{field(row,'correo')||'—'}}</td><td>{{userRole===1?'Administrador':userRole===3?'Trabajador':'Cliente'}}</td><td><span class="state-pill ok">Activo</span></td><td class="table-actions"><button @click="openEditUser(row)">Editar</button><button class="danger" @click="disableUser(row)">Deshabilitar</button></td></tr><tr v-if="!filteredUsers.length"><td colspan="6" class="empty-cell">No hay usuarios de este rol. Puedes crear el primero.</td></tr></tbody></table></div>
      </template>

      <template v-else-if="section==='productos'">
        <div class="admin-toolbar"><p>{{products.length}} productos encontrados</p><div class="toolbar-actions"><RouterLink class="secondary-button" to="/">Ver catálogo</RouterLink><button class="button small" :disabled="!categories.length" @click="openNewProduct">+ Crear producto</button></div></div>
        <p v-if="!categories.length" class="alert">Faltan los catálogos auxiliares. Ejecuta la carga de referencias de productos.</p>
        <form v-if="showProductForm" class="admin-editor" @submit.prevent="saveProduct">
          <header><div><p class="eyebrow">{{editingProductId?'Editar':'Nuevo'}} producto</p><h3>{{editingProductId?'Actualizar producto':'Crear producto'}}</h3></div><button type="button" class="icon-close" @click="showProductForm=false">×</button></header>
          <div class="admin-form-grid">
            <label class="wide">Nombre<input v-model="productForm.nombre" required></label>
            <label>Categoría<select v-model.number="productForm.categoriaId" :disabled="!!editingProductId" @change="changeCategory"><option v-for="row in categories" :key="rowId(row,'id','id_categoria')" :value="rowId(row,'id','id_categoria')">{{field(row,'nombre')}}</option></select></label>
            <label>Marca<select v-model.number="productForm.marcaId" required><option v-for="row in brands" :key="rowId(row,'id','marca_id')" :value="rowId(row,'id','marca_id')">{{field(row,'nombre')}}</option></select></label>
            <label>Gama<select v-model.number="productForm.gamaId"><option v-for="row in ranges" :key="rowId(row,'id','gama_id')" :value="rowId(row,'id','gama_id')">{{field(row,'nombre','tipo_gama')}}</option></select></label>
            <label>IVA<select v-model.number="productForm.ivaId"><option v-for="row in taxes" :key="rowId(row,'iva_id','id')" :value="rowId(row,'iva_id','id')">{{field(row,'porcentaje')}}%</option></select></label>
            <label>Precio<input v-model.number="productForm.precio" type="number" min="0" step="0.01" required></label><label>Costo<input v-model.number="productForm.costo" type="number" min="0" step="0.01" required></label>
            <label>Stock inicial<input v-model.number="productForm.stock" type="number" min="0" step="1" required></label><label>Enlace externo<input v-model="productForm.enlace" type="url"></label>
            <fieldset class="wide technical-fields"><legend>Especificaciones de la categoría</legend><p v-if="!selectedCategoryFields.length">Esta categoría no requiere especificaciones adicionales.</p><label v-for="spec in selectedCategoryFields" :key="spec.name">{{spec.label}}<input v-model="productAttributes[spec.name]" :type="spec.type" :min="spec.type==='number'?0:undefined" :step="spec.type==='number'?'1':undefined" :placeholder="spec.placeholder"></label></fieldset>
            <label class="wide">Imágenes (máximo 8 MB cada una)<input type="file" accept="image/*" multiple @change="selectImages"><small>{{productImages.length}} archivo(s) seleccionado(s). La primera será portada.</small></label>
            <section v-if="existingGallery.length" class="wide gallery-manager"><header><div><strong>Orden de imágenes</strong><small>Arrastra las imágenes para ordenarlas. La primera se usa como portada y en el menú.</small></div><button v-if="galleryOrderDirty" type="button" class="secondary-button" :disabled="saving" @click="saveImageOrder">Guardar orden</button></header><div class="admin-gallery"><figure v-for="(image,index) in existingGallery" :key="rowId(image,'galeriaId','galeria_id','id')" draggable="true" :class="{dragging:draggedImageIndex===index,'drop-target':imageDropIndex===index&&draggedImageIndex!==index}" @dragstart="startImageDrag(index,$event)" @dragenter.prevent="enterImageDrop(index)" @dragover.prevent @drop.prevent="dropImage(index)" @dragend="endImageDrag"><span>{{index===0?'Portada':index+1}}</span><img :src="`/api/galeria_v2/img/${rowId(image,'galeriaId','galeria_id','id')}`" draggable="false"><div><button type="button" :disabled="index===0" title="Mover a la izquierda" @click="moveImage(index,-1)">←</button><button type="button" :disabled="index===existingGallery.length-1" title="Mover a la derecha" @click="moveImage(index,1)">→</button><button type="button" class="remove-image" @click="removeImage(image)">Quitar</button></div></figure></div></section>
          </div>
          <div class="editor-actions"><button type="button" class="secondary-button" @click="showProductForm=false">Cancelar</button><button class="button small" :disabled="saving">{{saving?'Guardando…':'Guardar producto'}}</button></div>
        </form>
        <div class="admin-table-wrap"><table><thead><tr><th>ID</th><th>Producto</th><th>Precio</th><th>Stock</th><th>Estado</th><th>Acciones</th></tr></thead><tbody><tr v-for="row in products" :key="rowId(row,'producto_id','productoId','id')"><td>{{rowId(row,'producto_id','productoId','id')}}</td><td><strong>{{field(row,'nombre')}}</strong></td><td>${{Number(field(row,'preciounitario','precio')||0).toFixed(2)}}</td><td>{{field(row,'stock')??'—'}}</td><td><span class="state-pill" :class="{ok:field(row,'habilitado')!==false}">{{field(row,'habilitado')===false?'Deshabilitado':'Activo'}}</span></td><td class="table-actions"><button @click="openEditProduct(row)">Editar</button><button class="danger" @click="disableProduct(row)">Deshabilitar</button></td></tr><tr v-if="!products.length"><td colspan="6" class="empty-cell">El catálogo está vacío. Puedes crear el primer producto.</td></tr></tbody></table></div>
      </template>

      <template v-else><div class="node-grid"><article v-for="node in [{name:'Nodo 1',port:8091},{name:'Nodo 2',port:8092},{name:'Nodo 3',port:8093}]" :key="node.port" class="admin-card"><span class="node-dot"></span><h3>{{node.name}}</h3><p>CockroachDB · Puerto {{node.port}}</p><a :href="`https://localhost:${node.port}`" target="_blank">Abrir consola ↗</a></article></div></template>
    </div>
  </section>
</template>
