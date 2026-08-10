(function () {
  const ORD_BASE = 'http://localhost:8084';
  const PROD_BASE = 'http://localhost:8081';

  // ---------- helpers ----------
  const fmt = (n) => '$' + Number(n || 0).toFixed(2);

async function apiFetch(url, options = {}) {
    const res = await fetch(url, {
      credentials: 'include',
      ...options,
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    });
    if (!res.ok) {
      let msg = `HTTP ${res.status}`;
      try { const body = await res.json(); msg = body.detail || body.message || msg; } catch {}
      throw new Error(msg);
    }
    if (res.status === 204) return null;
    return res.json();
  }

  function usuarioId() {
    const id = window.getLoggedUserId ? window.getLoggedUserId() : null;
    if (!id) console.warn('[proveedores-compras] No se encontró usuario logueado (X-Usuario-Id).');
    return id;
  }
  function usuarioNombre() {
    return window.getLoggedUsername ? window.getLoggedUsername() : null;
  }

  // ---------- Sub-tabs ----------
 window.pcShowTab = function (tab) {
     const isProv = tab === 'proveedores';
     document.getElementById('pc_proveedores').style.display = isProv ? '' : 'none';
     document.getElementById('pc_compras').style.display = isProv ? 'none' : '';
     document.getElementById('pc_tab_proveedores_btn').classList.toggle('btn-primary', isProv);
     document.getElementById('pc_tab_proveedores_btn').classList.toggle('btn-outline', !isProv);
     document.getElementById('pc_tab_compras_btn').classList.toggle('btn-primary', !isProv);
    if (isProv) loadProveedores(); else loadOrdenesCompra();
  };

  // ==================================================================
  // PROVEEDORES
  // ==================================================================
  async function loadProveedores() {
    const tb = document.getElementById('tbProveedores');
    tb.innerHTML = `<tr><td colspan="8" class="tt-muted-row">Cargando…</td></tr>`;
    try {
      const list = await apiFetch(`${ORD_BASE}/api/proveedores`);
      if (!list.length) {
        tb.innerHTML = `<tr><td colspan="8" class="tt-muted-row">No hay proveedores activos.</td></tr>`;
        return;
      }
      tb.innerHTML = list.map((p) => `
        <tr>
          <td>${p.proveedorId}</td>
          <td>${p.nombre ?? ''}</td>
          <td>${p.ruc ?? ''}</td>
          <td>${p.contactoNombre ?? ''}</td>
          <td>${p.telefono ?? ''}</td>
          <td>${p.correo ?? ''}</td>
          <td>${p.activo ? '✅ Activo' : '⛔ Inactivo'}</td>
          <td>
            <button class="btn btn-outline btn--sm" onclick="pcEditarProveedor(${p.proveedorId})">Editar</button>
            <button class="btn btn-danger btn--sm" onclick="pcDesactivarProveedor(${p.proveedorId})">Desactivar</button>
          </td>
        </tr>`).join('');
    } catch (err) {
      tb.innerHTML = `<tr><td colspan="8" class="tt-muted-row">Error cargando proveedores: ${err.message}</td></tr>`;
    }
  }

  async function loadProveedoresSelect() {
    const sel = document.getElementById('oc_proveedor');
    sel.innerHTML = `<option value="">Cargando…</option>`;
    try {
      const list = await apiFetch(`${ORD_BASE}/api/proveedores`);
      sel.innerHTML = list.map((p) => `<option value="${p.proveedorId}">${p.nombre} (RUC ${p.ruc})</option>`).join('')
        || `<option value="">No hay proveedores activos</option>`;
    } catch (err) {
      sel.innerHTML = `<option value="">Error: ${err.message}</option>`;
    }
  }

  function abrirModalProveedor(proveedor) {
    document.getElementById('mdlProveedorTitle').textContent = proveedor ? 'Editar proveedor' : 'Agregar proveedor';
    document.getElementById('prov_id').value = proveedor?.proveedorId ?? '';
    document.getElementById('prov_nombre').value = proveedor?.nombre ?? '';
    document.getElementById('prov_ruc').value = proveedor?.ruc ?? '';
    document.getElementById('prov_contacto').value = proveedor?.contactoNombre ?? '';
    document.getElementById('prov_telefono').value = proveedor?.telefono ?? '';
    document.getElementById('prov_correo').value = proveedor?.correo ?? '';
    document.getElementById('prov_direccion').value = proveedor?.direccion ?? '';
    document.getElementById('modalProveedor').hidden = false;
  }

  window.pcEditarProveedor = async function (id) {
    try {
      const p = await apiFetch(`${ORD_BASE}/api/proveedores/${id}`);
      abrirModalProveedor(p);
    } catch (err) {
      alert('No se pudo cargar el proveedor: ' + err.message);
    }
  };

  window.pcDesactivarProveedor = async function (id) {
    if (!confirm('¿Desactivar este proveedor? No podrá usarse en nuevas órdenes de compra.')) return;
    try {
      await apiFetch(`${ORD_BASE}/api/proveedores/${id}`, { method: 'DELETE' });
      loadProveedores();
    } catch (err) {
      alert('No se pudo desactivar: ' + err.message);
    }
  };

  function initProveedoresUI() {
    document.getElementById('btnAgregarProveedor').addEventListener('click', () => abrirModalProveedor(null));
    document.getElementById('btnRefrescarProveedores').addEventListener('click', loadProveedores);
    document.getElementById('btnCerrarModalProveedor').addEventListener('click', () => { document.getElementById('modalProveedor').hidden = true; });
    document.getElementById('btnCancelarModalProveedor').addEventListener('click', () => { document.getElementById('modalProveedor').hidden = true; });

    document.getElementById('formProveedor').addEventListener('submit', async (e) => {
      e.preventDefault();
      const id = document.getElementById('prov_id').value;
      const body = {
        nombre: document.getElementById('prov_nombre').value.trim(),
        ruc: document.getElementById('prov_ruc').value.trim(),
        contactoNombre: document.getElementById('prov_contacto').value.trim() || null,
        telefono: document.getElementById('prov_telefono').value.trim() || null,
        correo: document.getElementById('prov_correo').value.trim() || null,
        direccion: document.getElementById('prov_direccion').value.trim() || null,
      };
      try {
        if (id) {
          await apiFetch(`${ORD_BASE}/api/proveedores/${id}`, { method: 'PUT', body: JSON.stringify(body) });
        } else {
          await apiFetch(`${ORD_BASE}/api/proveedores`, { method: 'POST', body: JSON.stringify(body) });
        }
        document.getElementById('modalProveedor').hidden = true;
        loadProveedores();
      } catch (err) {
        alert('No se pudo guardar el proveedor: ' + err.message);
      }
    });
  }

  // ==================================================================
  // PRODUCTOS (para elegir qué comprar)
  // ==================================================================
  let _catalogoCache = null;
  async function cargarCatalogoProductos() {
    if (_catalogoCache) return _catalogoCache;
    try {
      _catalogoCache = await apiFetch(`${PROD_BASE}/api/productos?page=0&size=200`);
    } catch (err) {
      console.error('No se pudo cargar el catálogo de productos', err);
      _catalogoCache = [];
    }
    return _catalogoCache;
  }

  function opcionesProductoHTML(productos) {
    return `<option value="">Seleccione…</option>` + productos.map((p) =>
      `<option value="${p.producto_id}" data-costo="${p.costo ?? p.preciounitario ?? 0}">${p.nombre}</option>`
    ).join('');
  }

  // ==================================================================
  // ÓRDENES DE COMPRA
 async function loadOrdenesCompra() {
    const tb = document.getElementById('tbOrdenesCompra');
    tb.innerHTML = `<tr><td colspan="8" class="tt-muted-row">Cargando…</td></tr>`;
    const estado = document.getElementById('oc_filtro_estado').value;
    try {
      const url = new URL(`${ORD_BASE}/api/ordenes-compra`);
      if (estado) url.searchParams.set('estado', estado);
      const list = await apiFetch(url.toString());
      if (!list.length) {
        tb.innerHTML = `<tr><td colspan="8" class="tt-muted-row">No hay órdenes de compra.</td></tr>`;
        return;
      }
      // proveedores para mostrar nombre en vez de solo el id
      const proveedores = await apiFetch(`${ORD_BASE}/api/proveedores`).catch(() => []);
      const nombreProv = (id) => proveedores.find((p) => p.proveedorId === id)?.nombre ?? `#${id}`;

      tb.innerHTML = list.map((o) => `
        <tr>
          <td>${o.numeroOrden ?? o.ordenCompraId}</td>
          <td>${nombreProv(o.proveedorId)}</td>
          <td>${o.fechaEmision ?? ''}</td>
          <td>${o.fechaEsperada ?? ''}</td>
          <td>${pcBadgeEstado(o.estado)}</td>
          <td>${fmt(o.totalPedido)}</td>
          <td>${fmt(o.total)}</td>
          <td>${pcAccionesOrden(o)}</td>
        </tr>`).join('');
    } catch (err) {
      tb.innerHTML = `<tr><td colspan="8" class="tt-muted-row">Error cargando órdenes: ${err.message}</td></tr>`;
    }
  }

  function pcBadgeEstado(estado) {
    const iconos = {
      PENDIENTE: '🟡', ENVIADA: '🔵', RECIBIDA_PARCIAL: '🟠', RECIBIDA: '🟢', CANCELADA: '🔴',
    };
    return `${iconos[estado] ?? ''} ${estado}`;
  }

  function pcAccionesOrden(o) {
    const botones = [];
    if (o.estado === 'PENDIENTE') {
      botones.push(`<button class="btn btn-primary btn--sm" onclick="pcEnviarOrden(${o.ordenCompraId})">Enviar</button>`);
      botones.push(`<button class="btn btn-danger btn--sm" onclick="pcCancelarOrden(${o.ordenCompraId})">Cancelar</button>`);
    }
    if (o.estado === 'ENVIADA' || o.estado === 'RECIBIDA_PARCIAL') {
      botones.push(`<button class="btn btn-outline btn--sm" onclick="pcAbrirRecepcion(${o.ordenCompraId})">Registrar recepción</button>`);
    }
    return botones.join(' ') || '—';
  }

  window.pcEnviarOrden = async function (id) {
    if (!confirm('¿Marcar esta orden como enviada al proveedor?')) return;
    try {
      await apiFetch(`${ORD_BASE}/api/ordenes-compra/${id}/enviar`, { method: 'POST' });
      loadOrdenesCompra();
    } catch (err) { alert('No se pudo enviar la orden: ' + err.message); }
  };

  window.pcCancelarOrden = async function (id) {
    if (!confirm('¿Cancelar esta orden de compra?')) return;
    try {
      await apiFetch(`${ORD_BASE}/api/ordenes-compra/${id}/cancelar`, { method: 'POST' });
      loadOrdenesCompra();
    } catch (err) { alert('No se pudo cancelar la orden: ' + err.message); }
  };

  // ----- Modal: nueva orden de compra -----
  function filaLineaOC(productos) {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td><select class="control oc-linea-producto">${opcionesProductoHTML(productos)}</select></td>
      <td><input type="number" min="1" step="1" class="control oc-linea-cantidad" value="1" /></td>
      <td><input type="number" min="0" step="0.01" class="control oc-linea-costo" value="0.00" /></td>
      <td class="oc-linea-subtotal">$0.00</td>
      <td><button type="button" class="btn btn-danger btn--sm" title="Quitar">✕</button></td>
    `;
    const selProd = tr.querySelector('.oc-linea-producto');
    const inCant = tr.querySelector('.oc-linea-cantidad');
    const inCosto = tr.querySelector('.oc-linea-costo');
    const subtotalCell = tr.querySelector('.oc-linea-subtotal');
    const btnQuitar = tr.querySelector('button');

    function recalcLinea() {
      const costo = parseFloat(inCosto.value || '0');
      const cant = parseInt(inCant.value || '0', 10);
      subtotalCell.textContent = fmt(costo * cant);
      recalcTotalOC();
    }
    selProd.addEventListener('change', () => {
      const opt = selProd.selectedOptions[0];
      const costoSugerido = opt?.dataset?.costo;
      if (costoSugerido) inCosto.value = Number(costoSugerido).toFixed(2);
      recalcLinea();
    });
    inCant.addEventListener('input', recalcLinea);
    inCosto.addEventListener('input', recalcLinea);
    btnQuitar.addEventListener('click', () => { tr.remove(); recalcTotalOC(); });

    return tr;
  }

  function recalcTotalOC() {
    const filas = document.querySelectorAll('#oc_detalle_body tr');
    let total = 0;
    filas.forEach((tr) => {
      const costo = parseFloat(tr.querySelector('.oc-linea-costo').value || '0');
      const cant = parseInt(tr.querySelector('.oc-linea-cantidad').value || '0', 10);
      total += costo * cant;
    });
    document.getElementById('oc_total_estimado').textContent = fmt(total);
  }

  async function abrirModalOrdenCompra() {
    await loadProveedoresSelect();
    const productos = await cargarCatalogoProductos();
    const body = document.getElementById('oc_detalle_body');
    body.innerHTML = '';
    body.appendChild(filaLineaOC(productos)); // arranca con una línea
    document.getElementById('oc_fecha_esperada').value = '';
    document.getElementById('oc_total_estimado').textContent = '$0.00';
    document.getElementById('modalOrdenCompra').hidden = false;
  }

  function initOrdenesUI() {
    document.getElementById('btnNuevaOrdenCompra').addEventListener('click', abrirModalOrdenCompra);
    document.getElementById('btnRefrescarOrdenes').addEventListener('click', loadOrdenesCompra);
    document.getElementById('oc_filtro_estado').addEventListener('change', loadOrdenesCompra);
    document.getElementById('btnCerrarModalOrden').addEventListener('click', () => { document.getElementById('modalOrdenCompra').hidden = true; });
    document.getElementById('btnCancelarModalOrden').addEventListener('click', () => { document.getElementById('modalOrdenCompra').hidden = true; });

    document.getElementById('btnAgregarLineaOC').addEventListener('click', async () => {
      const productos = await cargarCatalogoProductos();
      document.getElementById('oc_detalle_body').appendChild(filaLineaOC(productos));
    });

    document.getElementById('formOrdenCompra').addEventListener('submit', async (e) => {
      e.preventDefault();
      const proveedorId = Number(document.getElementById('oc_proveedor').value);
      const fechaEsperada = document.getElementById('oc_fecha_esperada').value || null;
      const filas = [...document.querySelectorAll('#oc_detalle_body tr')];

      if (!proveedorId) { alert('Selecciona un proveedor.'); return; }
      if (!filas.length) { alert('Agrega al menos un producto.'); return; }

      const detalle = filas.map((tr) => ({
        productoId: Number(tr.querySelector('.oc-linea-producto').value),
        cantidadPedida: parseInt(tr.querySelector('.oc-linea-cantidad').value || '0', 10),
        costoUnitario: parseFloat(tr.querySelector('.oc-linea-costo').value || '0'),
      }));
      if (detalle.some((d) => !d.productoId || d.cantidadPedida <= 0)) {
        alert('Revisa las líneas: falta producto o la cantidad no es válida.');
        return;
      }

      const uid = usuarioId();
      if (!uid) { alert('No se detectó el usuario logueado; no se puede crear la orden.'); return; }

      try {
        await apiFetch(`${ORD_BASE}/api/ordenes-compra`, {
          method: 'POST',
          headers: { 'X-Usuario-Id': String(uid) },
          body: JSON.stringify({ proveedorId, fechaEsperada, detalle }),
        });
        document.getElementById('modalOrdenCompra').hidden = true;
        loadOrdenesCompra();
      } catch (err) {
        alert('No se pudo crear la orden de compra: ' + err.message);
      }
    });
  }

  // ----- Modal: registrar recepción -----
  window.pcAbrirRecepcion = async function (ordenCompraId) {
    try {
      const orden = await apiFetch(`${ORD_BASE}/api/ordenes-compra/${ordenCompraId}`);
      const productos = await cargarCatalogoProductos();
      const nombreProd = (id) => productos.find((p) => p.producto_id === id)?.nombre ?? `#${id}`;

      document.getElementById('rec_orden_id').value = ordenCompraId;
      const body = document.getElementById('rec_detalle_body');
      const detalle = orden.detalle || [];
      body.innerHTML = detalle.map((d) => `
        <tr data-producto-id="${d.productoId}">
          <td>${nombreProd(d.productoId)}</td>
          <td>${d.cantidadPedida}</td>
          <td>${d.cantidadRecibida ?? 0}</td>
          <td><input type="number" min="0" step="1" class="control rec-cantidad-ahora" value="0" /></td>
        </tr>`).join('');
      document.getElementById('modalRecepcionOC').hidden = false;
    } catch (err) {
      alert('No se pudo cargar la orden: ' + err.message);
    }
  };

  function initRecepcionUI() {
    document.getElementById('btnCerrarModalRecepcion').addEventListener('click', () => { document.getElementById('modalRecepcionOC').hidden = true; });
    document.getElementById('btnCancelarModalRecepcion').addEventListener('click', () => { document.getElementById('modalRecepcionOC').hidden = true; });

    document.getElementById('formRecepcionOC').addEventListener('submit', async (e) => {
      e.preventDefault();
      const ordenId = document.getElementById('rec_orden_id').value;
      const filas = [...document.querySelectorAll('#rec_detalle_body tr')];
      const recepcion = {};
      filas.forEach((tr) => {
        const productoId = tr.dataset.productoId;
        const cantidad = parseInt(tr.querySelector('.rec-cantidad-ahora').value || '0', 10);
        if (cantidad > 0) recepcion[productoId] = cantidad;
      });
      if (!Object.keys(recepcion).length) {
        alert('Ingresa al menos una cantidad recibida.');
        return;
      }
      try {
        const url = new URL(`${ORD_BASE}/api/ordenes-compra/${ordenId}/recepcion`);
        const nombre = usuarioNombre();
        if (nombre) url.searchParams.set('usuario', nombre);
        await apiFetch(url.toString(), { method: 'POST', body: JSON.stringify(recepcion) });
        document.getElementById('modalRecepcionOC').hidden = true;
        loadOrdenesCompra();
      } catch (err) {
        alert('No se pudo registrar la recepción: ' + err.message);
      }
    });
  }

  // ---------- init ----------
  document.addEventListener('DOMContentLoaded', () => {
    if (!document.getElementById('proveedores')) return; // sección no está en esta página
    initProveedoresUI();
    initOrdenesUI();
    initRecepcionUI();
    // si la sección ya está activa al cargar (poco probable, pero por si acaso)
    if (document.getElementById('proveedores').classList.contains('active')) {
      loadProveedores();
    }
  });
})();