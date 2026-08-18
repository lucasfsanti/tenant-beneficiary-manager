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
  return true
})

export default router
