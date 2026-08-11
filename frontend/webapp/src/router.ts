import { createRouter, createWebHashHistory } from 'vue-router'
import CatalogView from './views/CatalogView.vue'
import LoginView from './views/LoginView.vue'
import ProductDetailView from './views/ProductDetailView.vue'
import CartView from './views/CartView.vue'
import AdminView from './views/AdminView.vue'
import BuilderView from './views/BuilderView.vue'
import RegisterView from './views/RegisterView.vue'
import RecoveryView from './views/RecoveryView.vue'
import AccountView from './views/AccountView.vue'
import CheckoutView from './views/CheckoutView.vue'
import InvoiceView from './views/InvoiceView.vue'
import WorkerView from './views/WorkerView.vue'
import { getUser } from './services/session'

export const router = createRouter({
  history: createWebHashHistory('/app/'),
  routes: [
    { path: '/', name: 'catalog', component: CatalogView },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/producto/:id', name: 'product', component: ProductDetailView },
    { path: '/carrito', name: 'cart', component: CartView },
    { path: '/admin', name: 'admin', component: AdminView, meta: { admin: true } },
    { path: '/armado', name: 'builder', component: BuilderView },
    { path: '/registro', name: 'register', component: RegisterView },
    { path: '/recuperacion', name: 'recovery', component: RecoveryView },
    { path: '/cuenta', name: 'account', component: AccountView },
    { path: '/pago', name: 'checkout', component: CheckoutView },
    { path: '/factura/:id?', name: 'invoice', component: InvoiceView },
    { path: '/trabajador', name: 'worker', component: WorkerView },
  ],
})

router.beforeEach((to) => {
  if (!to.meta.admin) return true
  const user = getUser()
  const roleId = Number(user?.id_rol ?? user?.idRol ?? 0)
  const roleName = String(user?.rol ?? user?.role ?? '').toLowerCase()
  return roleId === 1 || roleName === 'admin'
    ? true
    : { path: '/login', query: { next: to.fullPath } }
})
