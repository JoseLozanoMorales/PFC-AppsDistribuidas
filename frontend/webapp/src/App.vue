<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { clearSession, getUser } from './services/session'

const router = useRouter()
const user = ref(getUser())
const dark = ref(localStorage.getItem('tt-theme') === 'dark')
const userLabel = computed(() => user.value?.nombre || user.value?.usuario || 'Mi cuenta')
const isAdmin = computed(() => {
  const roleId = Number(user.value?.id_rol ?? user.value?.idRol ?? 0)
  const roleName = String(user.value?.rol ?? user.value?.role ?? '').toLowerCase()
  return roleId === 1 || roleName === 'admin'
})

function toggleTheme() {
  dark.value = !dark.value
  localStorage.setItem('tt-theme', dark.value ? 'dark' : 'light')
}

function logout() {
  clearSession()
  user.value = null
  router.push('/login')
}
</script>

<template>
  <div class="app-shell" :class="{ dark }">
    <header class="topbar">
      <RouterLink class="brand" to="/"><span>TT</span> TiendaTech</RouterLink>
      <nav aria-label="Navegación principal">
        <RouterLink to="/">Productos</RouterLink>
        <RouterLink to="/armado">Arma tu PC</RouterLink>
        <RouterLink to="/carrito">Carrito</RouterLink>
        <RouterLink v-if="isAdmin" to="/admin">Administración</RouterLink>
      </nav>
      <div class="actions">
        <button class="icon-button" type="button" :aria-label="dark ? 'Usar tema claro' : 'Usar tema oscuro'" @click="toggleTheme">
          {{ dark ? '☀' : '☾' }}
        </button>
        <template v-if="user"><RouterLink class="text-button" to="/cuenta">{{ userLabel }}</RouterLink><button class="text-button" type="button" @click="logout">Salir</button></template>
        <RouterLink v-else class="button small" to="/login">Ingresar</RouterLink>
      </div>
    </header>
    <main><RouterView @session-changed="user = getUser()" /></main>
    <footer>© 2026 TiendaTech · Arquitectura de microservicios</footer>
  </div>
</template>
