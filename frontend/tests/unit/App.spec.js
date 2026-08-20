import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import App from '../../src/App.vue'
import { useAuthStore } from '../../src/stores/auth'

// TenantSwitcher (rendered in the header for an authenticated user) calls this on mount for a
// System Admin — mocked so tests don't make a real network call.
vi.mock('../../src/services/tenantAdminApi', () => ({
  default: {
    listTenants: vi.fn(() => Promise.resolve({ data: [] }))
  }
}))

describe('App', () => {
  let router

  beforeEach(async () => {
    setActivePinia(createPinia())
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/login', name: 'login', component: { template: '<div />' } },
        { path: '/pessoas', component: { template: '<div />' } },
        { path: '/beneficiarios', component: { template: '<div />' } },
        { path: '/tenants', component: { template: '<div />' } },
        { path: '/admins', component: { template: '<div />' } },
        { path: '/tenants/:id/editar', component: { template: '<div />' } }
      ]
    })
    await router.push('/')
    await router.isReady()
  })

  function mountApp() {
    return mount(App, { global: { plugins: [router] } })
  }

  it('shows no header/nav when the user is not authenticated', () => {
    const wrapper = mountApp()

    expect(wrapper.find('.app-header').exists()).toBe(false)
  })

  it('shows the Tenants and Administradores links for a System Admin', () => {
    const auth = useAuthStore()
    auth.token = 'fake-token'
    auth.user = { id: 'user-1', username: 'admin', isSystemAdmin: true, tenants: [] }

    const wrapper = mountApp()

    expect(wrapper.find('.app-header').exists()).toBe(true)
    const links = wrapper.findAll('a').map((a) => a.text())
    expect(links).toContain('Tenants')
    expect(links).toContain('Administradores')
    expect(links).not.toContain('Meu Tenant')
  })

  it('shows "Meu Tenant" instead, for a Tenant Admin who is not a System Admin', () => {
    const auth = useAuthStore()
    auth.token = 'fake-token'
    auth.user = {
      id: 'user-2',
      username: 'bruno',
      isSystemAdmin: false,
      tenants: [{ id: 'tenant-a', name: 'Tenant Alfa', isTenantAdmin: true }]
    }
    auth.activeTenantId = 'tenant-a'

    const wrapper = mountApp()

    const links = wrapper.findAll('a').map((a) => a.text())
    expect(links).toContain('Meu Tenant')
    expect(links).not.toContain('Tenants')
    expect(links).not.toContain('Administradores')
  })

  it('shows neither admin link for a plain Normal user', () => {
    const auth = useAuthStore()
    auth.token = 'fake-token'
    auth.user = {
      id: 'user-3',
      username: 'ana',
      isSystemAdmin: false,
      tenants: [{ id: 'tenant-a', name: 'Tenant Alfa', isTenantAdmin: false }]
    }
    auth.activeTenantId = 'tenant-a'

    const wrapper = mountApp()

    const links = wrapper.findAll('a').map((a) => a.text())
    expect(links).not.toContain('Tenants')
    expect(links).not.toContain('Administradores')
    expect(links).not.toContain('Meu Tenant')
  })

  it('logs out and navigates to /login when "Sair" is clicked', async () => {
    const auth = useAuthStore()
    auth.token = 'fake-token'
    auth.user = { id: 'user-1', username: 'admin', isSystemAdmin: true, tenants: [] }
    auth.logout = vi.fn(auth.logout)

    const wrapper = mountApp()
    await wrapper.find('button.button--secondary').trigger('click')
    await flushPromises()

    expect(auth.logout).toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
