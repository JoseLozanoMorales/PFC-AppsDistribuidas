<script setup lang="ts">
import { onMounted,ref } from 'vue'
import { api } from '../services/api'
type Row=Record<string,unknown>
const movements=ref<Row[]>([]),products=ref<Row[]>([]),loading=ref(true)
onMounted(async()=>{[movements.value,products.value]=await Promise.all([api<Row[]>('/api/movimientos').catch(()=>[]),api<Row[]>('/api/productos?size=50').catch(()=>[])]);loading.value=false})
</script>
<template><section class="worker-page"><header><p class="eyebrow">Operaciones</p><h1>Panel de trabajador</h1><p>Consulta rápida del catálogo e inventario distribuido.</p></header><p v-if="loading" class="status">Consultando servicios CRDB…</p><div v-else class="metric-grid"><article><span>Productos visibles</span><strong>{{products.length}}</strong><small>Productos en CRDB</small></article><article><span>Movimientos</span><strong>{{movements.length}}</strong><small>Registros de inventario</small></article></div><div class="admin-columns"><article class="admin-card"><h3>Catálogo</h3><p>Consulta componentes y disponibilidad desde la interfaz principal.</p><RouterLink class="button small" to="/">Ver productos</RouterLink></article><article class="admin-card"><h3>Armado</h3><p>Comprueba cómo se presentan los componentes a clientes.</p><RouterLink class="button small" to="/armado">Abrir armador</RouterLink></article></div></section></template>
