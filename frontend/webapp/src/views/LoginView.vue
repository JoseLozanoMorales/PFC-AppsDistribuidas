<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError } from '../services/api'
import { saveSession, type SessionUser } from '../services/session'

const emit = defineEmits<{ 'session-changed': [] }>()
const route = useRoute()
const router = useRouter()
const usuario = ref('')
const contrasena = ref('')
const busy = ref(false)
const error = ref('')

interface LoginResponse { user?: SessionUser; usuario?: SessionUser; token?: string; access?: string; accessToken?: string; message?: string }

async function submit() {
  error.value = ''
  busy.value = true
  try {
    const data = await api<LoginResponse>('/api/login', {
      method: 'POST',
      body: JSON.stringify({ usuario: usuario.value.trim(), contrasena: contrasena.value, contrasenia: contrasena.value, password: contrasena.value }),
    })
    const user = data.user || data.usuario
    if (!user) throw new ApiError(data.message || 'La respuesta no contiene un usuario.', 422)
    saveSession(user, data.token || data.access || data.accessToken)
    emit('session-changed')
    if (typeof route.query.next === 'string') {
      await router.push(route.query.next)
    } else if (Number(user.id_rol ?? user.idRol ?? 0) === 1 || String(user.rol ?? user.role ?? '').toLowerCase() === 'admin') {
      await router.push('/admin')
    } else {
      await router.push('/')
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'No fue posible iniciar sesión.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="login-page">
    <div class="login-art" aria-hidden="true"><span>Tu próximo equipo<br />empieza aquí.</span></div>
    <form class="login-card" @submit.prevent="submit">
      <p class="eyebrow">Bienvenido de vuelta</p>
      <h1>Inicia sesión</h1>
      <p class="muted">Accede a tu carrito, pedidos y configuraciones.</p>
      <label>Usuario<input v-model="usuario" autocomplete="username" required placeholder="Ingresa tu usuario" /></label>
      <label>Contraseña<input v-model="contrasena" type="password" autocomplete="current-password" required placeholder="Ingresa tu contraseña" /></label>
      <p v-if="error" class="alert" role="alert">{{ error }}</p>
      <button class="button" :disabled="busy">{{ busy ? 'Validando…' : 'Entrar' }}</button>
      <div class="form-links"><RouterLink to="/registro">Crear cuenta</RouterLink><RouterLink to="/recuperacion">Olvidé mi contraseña</RouterLink></div>
    </form>
  </section>
</template>
