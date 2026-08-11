export interface SessionUser {
  usuarioId?: number
  id?: number
  usuario?: string
  nombre?: string
  rol?: string
  role?: string
  id_rol?: number
  idRol?: number
}

const USER_KEYS = ['tt_user', 'user', 'usuario', 'currentUser']

export function getUser(): SessionUser | null {
  for (const key of USER_KEYS) {
    const raw = sessionStorage.getItem(key) || localStorage.getItem(key)
    if (!raw) continue
    try {
      const parsed = JSON.parse(raw)
      return parsed.user || parsed.data || parsed
    } catch {
      return null
    }
  }
  return null
}

export function saveSession(user: SessionUser, token?: string): void {
  sessionStorage.setItem('user', JSON.stringify(user))
  sessionStorage.setItem('tt_user', JSON.stringify(user))
  if (token) localStorage.setItem('token', token)
}

export function clearSession(): void {
  USER_KEYS.forEach((key) => {
    sessionStorage.removeItem(key)
    localStorage.removeItem(key)
  })
  ;['access', 'token', 'refreshToken', 'usuarioId', 'usuario'].forEach((key) => {
    sessionStorage.removeItem(key)
    localStorage.removeItem(key)
  })
}

export function token(): string {
  return sessionStorage.getItem('access') || localStorage.getItem('access') ||
    sessionStorage.getItem('token') || localStorage.getItem('token') || ''
}
