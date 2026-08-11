<script setup lang="ts">
import { computed,onMounted,ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../services/api'
type Row=Record<string,unknown>
const route=useRoute(),invoice=ref<Row|null>(null),loading=ref(true),error=ref('')
const id=computed(()=>String(route.params.id||route.query.id||route.query.facturaId||'')),field=(...keys:string[])=>keys.map(k=>invoice.value?.[k]).find(v=>v!==undefined&&v!==null),money=(v:unknown)=>Number(v||0).toLocaleString('es-EC',{style:'currency',currency:'USD'})
onMounted(async()=>{if(!id.value){error.value='No se indicó una factura.';loading.value=false;return}try{invoice.value=await api<Row>(`/api/facturas/${encodeURIComponent(id.value)}`)}catch(cause){error.value=cause instanceof Error?cause.message:'No se pudo cargar la factura.'}finally{loading.value=false}})
</script>
<template><section class="invoice-page"><RouterLink class="back-link" to="/cuenta">← Volver a mi cuenta</RouterLink><p v-if="loading" class="status">Cargando factura…</p><div v-else-if="invoice" class="invoice-sheet"><header><div><p class="eyebrow">Comprobante</p><h1>Factura #{{field('facturaId','factura_id','id')||id}}</h1></div><div class="brand"><span>TT</span>TiendaTech</div></header><div class="invoice-meta"><p><small>Cliente</small><strong>{{field('cliente','nombreCliente','usuario')||'Cliente TiendaTech'}}</strong></p><p><small>Fecha</small><strong>{{field('fecha','creadoEn','creado_en')||'—'}}</strong></p><p><small>Estado</small><strong>{{field('estado')||'Emitida'}}</strong></p></div><div class="invoice-total"><span>Total</span><strong>{{money(field('total'))}}</strong></div><a class="button" :href="`/api/facturas/${encodeURIComponent(id)}/pdf`">Descargar PDF</a></div><p v-else class="alert">{{error}}</p></section></template>
