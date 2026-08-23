import { clearSession, getUser, saveToken, token } from './session'

// Shape comun de los endpoints de listado paginados (pedidos-service:
// PageResponse<T> -- content/page/size/totalElements/totalPages). Los
// servicios que consumen estos endpoints extraen `.content` y siguen
// devolviendo el array a quien los llama, para no propagar el cambio a
// todas las vistas.
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export class ApiError extends Error {
  constructor(message: string, public status: number) {
    super(message)
  }
}

let refreshInFlight: Promise<string> | null = null

async function renewAccessToken(): Promise<string> {
  if (!refreshInFlight) {
    refreshInFlight = fetch('/auth/refresh', { method: 'POST', credentials: 'include' })
      .then(async (response) => {
        if (!response.ok) throw new ApiError('La sesión expiró.', response.status)
        const data = await response.json() as { access?: string }
        if (!data.access) throw new ApiError('No se recibió un nuevo token.', 401)
        saveToken(data.access)
        return data.access
      })
      .finally(() => { refreshInFlight = null })
  }
  return refreshInFlight
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  const jwt = token()
  const user = getUser()
  const userId = user?.usuarioId ?? user?.id

  if (jwt && jwt !== 'mock') headers.set('Authorization', `Bearer ${jwt}`)
  if (userId) {
    headers.set('X-User-Id', String(userId))
    headers.set('X-Usuario-Id', String(userId))
  }
  if (user?.usuario) headers.set('X-Usuario', user.usuario)
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')

  let response: Response
  try {
    response = await fetch(path, { ...options, headers, credentials: 'include' })
  } catch {
    throw new ApiError('No se pudo conectar con el servidor.', 0)
  }

  if (response.status === 401 && path !== '/api/login' && path !== '/auth/refresh') {
    try {
      const renewedToken = await renewAccessToken()
      headers.set('Authorization', `Bearer ${renewedToken}`)
      response = await fetch(path, { ...options, headers, credentials: 'include' })
    } catch {
      clearSession()
    }
  }
  if (response.status === 401) clearSession()
  const text = await response.text()
  let body: unknown = null
  if (text) {
    try { body = JSON.parse(text) } catch { body = text }
  }
  if (!response.ok) {
    const data = body as { message?: string; error?: string } | null
    throw new ApiError(data?.message || data?.error || String(body || `Error ${response.status}`), response.status)
  }
  return body as T
}
