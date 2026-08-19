import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/', redirect: '/pessoas' },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/criar-conta',
    name: 'create-user',
    component: () => import('../views/CreateUserView.vue'),
    meta: { public: true }
  },
  {
    path: '/pessoas',
    name: 'pessoas-list',
    component: () => import('../views/PessoaListView.vue')
  },
  {
    path: '/pessoas/novo',
    name: 'pessoas-new',
    component: () => import('../views/PessoaFormView.vue')
  },
  {
    path: '/pessoas/:id/editar',
    name: 'pessoas-edit',
    component: () => import('../views/PessoaFormView.vue'),
    props: true
  },
  {
    path: '/beneficiarios',
    name: 'beneficiarios-list',
    component: () => import('../views/BeneficiarioListView.vue')
  },
  {
    path: '/beneficiarios/novo',
    name: 'beneficiarios-new',
    component: () => import('../views/BeneficiarioFormView.vue')
  },
  {
    path: '/beneficiarios/:id/editar',
    name: 'beneficiarios-edit',
    component: () => import('../views/BeneficiarioFormView.vue'),
    props: true
  },
  {
    path: '/tenants',
    name: 'tenants-list',
    component: () => import('../views/TenantListView.vue'),
    meta: { requiresSystemAdmin: true }
  },
  {
    path: '/tenants/novo',
    name: 'tenants-new',
    component: () => import('../views/TenantFormView.vue'),
    meta: { requiresSystemAdmin: true }
  },
  {
    path: '/tenants/:id/editar',
    name: 'tenants-edit',
    component: () => import('../views/TenantFormView.vue'),
    props: true,
    // System Admin, or the Tenant Admin of this specific tenant (FR-004/FR-005(b)) — not
    // System-Admin-exclusive like the other /tenants routes above.
    meta: { requiresSystemAdminOrTenantAdminOf: 'id' }
  },
  {
    path: '/admins',
    name: 'system-admins',
    component: () => import('../views/SystemAdminsView.vue'),
    meta: { requiresSystemAdmin: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    return { path: '/pessoas' }
  }
  if (to.meta.requiresSystemAdmin && !auth.isSystemAdmin) {
    return { path: '/pessoas' }
  }
  if (to.meta.requiresSystemAdminOrTenantAdminOf) {
    const tenantId = to.params[to.meta.requiresSystemAdminOrTenantAdminOf]
    if (!auth.isSystemAdmin && !auth.isTenantAdminFor(tenantId)) {
      return { path: '/pessoas' }
    }
  }
  return true
})

export default router
