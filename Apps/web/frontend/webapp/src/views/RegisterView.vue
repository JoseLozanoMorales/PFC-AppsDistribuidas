<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../services/api'

const router = useRouter()
const form = reactive({ nombre:'', usuario:'', correo:'', contrasena:'', repetir:'', cedula:'', telefono:'' })
const stage = ref<'form'|'otp'|'done'>('form')
const otp = ref('')
const txId = ref('')
const busy = ref(false)
const error = ref('')

async function requestOtp() {
  error.value = ''
  if (form.contrasena !== form.repetir) { error.value = 'Las contraseñas no coinciden.'; return }
  if (!/^\d{10}$/.test(form.cedula) || !/^\d{10}$/.test(form.telefono)) { error.value = 'Cédula y teléfono deben tener 10 dígitos.'; return }
  busy.value = true
  try {
    const result = await api<{txId:string; devCode?:string; mailSent?:boolean}>('/api/otp',{method:'POST',body:JSON.stringify({accion:'enviar',correo:form.correo,txId:txId.value || undefined})})
    txId.value = result.txId
    stage.value = 'otp'
    if (result.devCode) {
      error.value = `SMTP bloqueado en esta red. Codigo de prueba: ${result.devCode}`
    }
  } catch (cause) { error.value = cause instanceof Error ? cause.message : 'No se pudo enviar el código.' }
  finally { busy.value = false }
}

async function verify() {
  if (!/^\d{6}$/.test(otp.value)) { error.value = 'Ingresa el código de 6 dígitos.'; return }
  busy.value = true; error.value = ''
  try {
    await api<void>('/api/otp',{method:'POST',body:JSON.stringify({accion:'validar',correo:form.correo,codigo:otp.value,txId:txId.value})})
    await api('/api/usuarios/crear',{method:'POST',body:JSON.stringify({...form,otpTxId:txId.value})})
    stage.value = 'done'
    window.setTimeout(() => router.push('/login'), 1400)
  } catch (cause) { error.value = cause instanceof Error ? cause.message : 'No se pudo crear la cuenta.' }
  finally { busy.value = false }
}
</script>
<template><section class="auth-page"><div class="auth-intro"><p class="eyebrow">Únete a TiendaTech</p><h1>Tu próximo equipo empieza con una cuenta.</h1><p>Guarda configuraciones, gestiona pedidos y compra componentes desde un solo lugar.</p></div><form v-if="stage==='form'" class="auth-form" @submit.prevent="requestOtp"><p class="eyebrow">Registro</p><h2>Crear cuenta</h2><div class="form-grid"><label>Nombre completo<input v-model="form.nombre" required /></label><label>Usuario<input v-model="form.usuario" required /></label><label>Correo<input v-model="form.correo" type="email" required /></label><label>Teléfono<input v-model="form.telefono" inputmode="numeric" maxlength="10" required /></label><label>Cédula<input v-model="form.cedula" inputmode="numeric" maxlength="10" required /></label><span></span><label>Contraseña<input v-model="form.contrasena" type="password" minlength="8" required /></label><label>Repetir contraseña<input v-model="form.repetir" type="password" required /></label></div><p v-if="error" class="alert">{{ error }}</p><button class="button" :disabled="busy">{{busy?'Enviando…':'Verificar correo'}}</button><p class="auth-switch">¿Ya tienes cuenta? <RouterLink to="/login">Inicia sesión</RouterLink></p></form><div v-else-if="stage==='otp'" class="auth-form compact"><p class="eyebrow">Verificación</p><h2>Revisa tu correo</h2><p>Enviamos un código de seis dígitos a <strong>{{form.correo}}</strong>.</p><label>Código<input v-model="otp" class="otp-input" inputmode="numeric" maxlength="6" /></label><p v-if="error" class="alert">{{error}}</p><button class="button" :disabled="busy" @click="verify">{{busy?'Validando…':'Crear mi cuenta'}}</button><button class="link-button" @click="stage='form'">Corregir datos</button></div><div v-else class="auth-form compact success-panel"><span>✓</span><h2>Cuenta creada</h2><p>Te llevaremos al inicio de sesión.</p></div></section></template>
