(function () {
  'use strict';

  const nativeFetch = window.fetch.bind(window);
  const API_PREFIXES = ['/api/', '/auth/'];

  function getStoredUser() {
    const keys = ['tt_user', 'user', 'usuario', 'currentUser'];
    for (const key of keys) {
      const raw = sessionStorage.getItem(key) || localStorage.getItem(key);
      if (!raw) continue;
      try {
        const parsed = JSON.parse(raw);
        return parsed.user || parsed.data || parsed;
      } catch (_) {
        return null;
      }
    }
    return null;
  }

  function currentUserId() {
    const user = getStoredUser();
    return user?.usuarioId || user?.usuario_id || user?.id || sessionStorage.getItem('usuarioId') || localStorage.getItem('usuarioId');
  }

  function currentUsername() {
    const user = getStoredUser();
    return user?.usuario || user?.username || localStorage.getItem('usuario') || sessionStorage.getItem('usuario') || '';
  }

  function token() {
    return sessionStorage.getItem('access') ||
      localStorage.getItem('access') ||
      sessionStorage.getItem('token') ||
      localStorage.getItem('token') ||
      '';
  }

  function isApiUrl(url) {
    const value = typeof url === 'string' ? url : String(url?.url || '');
    return API_PREFIXES.some(prefix => value.startsWith(prefix));
  }

  function authHeaders(extra) {
    const headers = new Headers(extra || {});
    const jwt = token();
    if (jwt && jwt !== 'mock' && !headers.has('Authorization')) {
      headers.set('Authorization', `Bearer ${jwt}`);
    }
    const uid = currentUserId();
    if (uid && !headers.has('X-User-Id')) headers.set('X-User-Id', uid);
    if (uid && !headers.has('X-Usuario-Id')) headers.set('X-Usuario-Id', uid);
    const username = currentUsername();
    if (username && !headers.has('X-Usuario')) headers.set('X-Usuario', username);
    return headers;
  }

  async function readResponse(response) {
    if (response.status === 204) return null;
    const text = await response.text();
    if (!text) return null;
    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
      try { return JSON.parse(text); } catch (_) { return text; }
    }
    try { return JSON.parse(text); } catch (_) { return text; }
  }

  function apiError(response, body) {
    const message = body?.message || body?.error || body || `HTTP ${response.status}`;
    const err = new Error(String(message));
    err.status = response.status;
    err.body = body;
    return err;
  }

  async function apiFetch(url, options = {}) {
    const opts = { ...options };
    const body = opts.body;
    const headers = authHeaders(opts.headers);

    if (body && !(body instanceof FormData) && !(body instanceof URLSearchParams) && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json');
    }

    const response = await nativeFetch(url, {
      ...opts,
      headers,
      credentials: opts.credentials || 'include'
    });

    if (response.status === 401) {
      sessionStorage.clear();
      localStorage.removeItem('access');
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
      if (!location.pathname.endsWith('/Login.html')) location.href = '/Login.html';
    }

    return response;
  }

  async function apiJson(url, options = {}) {
    let response;
    try {
      response = await apiFetch(url, options);
    } catch (err) {
      throw new Error('No se pudo conectar con el servidor');
    }
    const body = await readResponse(response);
    if (!response.ok) throw apiError(response, body);
    return body;
  }

  async function carritoActual() {
    const uid = currentUserId();
    if (!uid) {
      const err = new Error('Debe iniciar sesion');
      err.status = 401;
      throw err;
    }
    return apiJson(`/api/carrito/${encodeURIComponent(uid)}`);
  }

  async function detalleCarritoActual() {
    const carrito = await carritoActual();
    const respuesta = await apiJson(`/api/carrito/${encodeURIComponent(carrito.carritoId)}/detalle`);
    // pedidos-service pagina este endpoint: devuelve {content, page, size,
    // totalElements, totalPages} en vez de un array plano. Se extrae
    // .content; si la forma es realmente otra cosa, se avisa en vez de
    // devolver [] en silencio (eso escondia el problema real).
    if (Array.isArray(respuesta)) return respuesta;
    if (respuesta && Array.isArray(respuesta.content)) return respuesta.content;
    throw new Error('Respuesta inesperada del carrito (se esperaba una lista).');
  }

  async function precioProducto(productoId) {
    const producto = await apiJson(`/api/productos/${encodeURIComponent(productoId)}`);
    return Number(producto.preciounitario ?? producto.precioUnitario ?? producto.precio ?? producto.costo ?? 0);
  }

  async function addCarritoItem(body) {
    const carrito = await carritoActual();
    const productoId = Number(body.productoId);
    const cantidad = Number(body.cantidad || 1);
    const precioUnitario = Number(body.precioUnitario ?? await precioProducto(productoId));
    await apiJson(`/api/carrito/${encodeURIComponent(carrito.carritoId)}/agregar`, {
      method: 'POST',
      body: JSON.stringify({ productoId, cantidad, precioUnitario })
    });
    return { ok: true };
  }

  async function removeCarritoItem(productoId) {
    const carrito = await carritoActual();
    await apiJson(`/api/carrito/${encodeURIComponent(carrito.carritoId)}/quitar/${encodeURIComponent(productoId)}`, {
      method: 'DELETE'
    });
    return { ok: true };
  }

  async function resumenCarrito() {
    const detalle = await detalleCarritoActual();
    const total = detalle.reduce((sum, item) => sum + Number(item.precioUnitario || 0) * Number(item.cantidad || 0), 0);
    return { items: detalle.length, total, subtotal: total };
  }

  function jsonResponse(body, status = 200) {
    return new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' }
    });
  }

  function pendingResponse(path) {
    return jsonResponse({
      ok: false,
      status: 'pendiente',
      message: `No existe una API en los microservicios para ${path}`
    }, 501);
  }

  async function compatibilityFetch(url, options = {}) {
    const method = String(options.method || 'GET').toUpperCase();
    const raw = typeof url === 'string' ? url : String(url?.url || '');
    const path = raw.startsWith(location.origin) ? raw.slice(location.origin.length) : raw;

    if ((path === '/auth/logout' || path === '/auth/logout') && method === 'POST') {
      return apiFetch('/auth/logout', options);
    }
    if (path === '/api/carrito/items' && method === 'GET') {
      return jsonResponse(await detalleCarritoActual());
    }
    if ((path === '/api/carrito/items' || path === '/api/carrito/agregar') && method === 'POST') {
      const body = typeof options.body === 'string' ? JSON.parse(options.body || '{}') : (options.body || {});
      return jsonResponse(await addCarritoItem(body));
    }
    const removeMatch = path.match(/^\/api\/carrito\/items\/([^/?#]+)/);
    if (removeMatch && method === 'DELETE') {
      return jsonResponse(await removeCarritoItem(decodeURIComponent(removeMatch[1])));
    }
    if (path === '/api/carrito/resumen' && method === 'GET') {
      return jsonResponse(await resumenCarrito());
    }
    if (/^\/api\/(busqueda|report|pagos|tipo_metodopago|mis-metodos-pago|encuesta|sugerencias)(\/|$)/.test(path)) {
      return pendingResponse(path);
    }
    if (/^\/api\/usuarios\/[^/]+\/(avatar|sugerencias)(\/|$)/.test(path)) {
      return pendingResponse(path);
    }
    if (/^\/api\/usuarios\/\d+$/.test(path)) {
      return pendingResponse(path);
    }
    if (/^\/api\/facturas\/[^/]+\/pdf$/.test(path)) {
      return pendingResponse(path);
    }
    return null;
  }

  window.apiFetch = apiFetch;
  window.apiJson = apiJson;
  window.authHeaders = () => Object.fromEntries(authHeaders().entries());
  window.currentUserId = currentUserId;

  window.fetch = async function patchedFetch(url, options = {}) {
    if (!isApiUrl(url)) return nativeFetch(url, options);
    const compat = await compatibilityFetch(url, options);
    if (compat) return compat;
    return apiFetch(url, options);
  };
})();
