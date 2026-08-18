import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import TenantFormView from '../../src/views/TenantFormView.vue'
import { useTenantStore } from '../../src/stores/tenant'
import { useAuthStore } from '../../src/stores/auth'

vi.mock('../../src/services/tenantAdminApi', () => ({
  default: {
    getTenant: vi.fn(() => Promise.resolve({ data: { id: 'tenant-1', name: 'Tenant Alfa' } })),
    listMembers: vi.fn(() => Promise.resolve({ data: [] }))
  }
}))

describe('TenantFormView member management', () => {
  let router

  beforeEach(async () => {
    setActivePinia(createPinia())
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/tenants', component: { template: '<div />' } },
        { path: '/pessoas', component: { template: '<div />' } }
      ]
    })
    await router.push('/')
    await router.isReady()
  })

  async function mountView() {
    const auth = useAuthStore()
    auth.user = { id: 'admin-1', username: 'admin', isSystemAdmin: true, tenants: [] }

    const wrapper = mount(TenantFormView, {
      props: { id: 'tenant-1' },
      global: { plugins: [router] }
    })
    await flushPromises()

    const store = useTenantStore()
    store.members = [{ userId: 'user-2', username: 'ana', isTenantAdmin: false }]
    store.searchUser = vi.fn(() => Promise.resolve([{ id: 'user-3', username: 'carla' }]))
    store.addMember = vi.fn(() => Promise.resolve(true))
    store.removeMember = vi.fn(() => Promise.resolve(true))
    store.grantTenantAdmin = vi.fn(() => Promise.resolve(true))
    store.revokeTenantAdmin = vi.fn(() => Promise.resolve(true))
    store.fetchMembers = vi.fn(() => Promise.resolve())
    await wrapper.vm.$nextTick()

    return { wrapper, store }
  }

  it('calls tenant.addMember when the add-member form is submitted', async () => {
    const { wrapper, store } = await mountView()

    await wrapper.find('.add-member-form input').setValue('carla')
    await wrapper.find('.add-member-form').trigger('submit.prevent')
    await flushPromises()

    expect(store.searchUser).toHaveBeenCalledWith('carla')
    expect(store.addMember).toHaveBeenCalledWith('tenant-1', 'user-3')
  })

  it('calls tenant.removeMember when a member row is removed', async () => {
    const { wrapper, store } = await mountView()
    window.confirm = vi.fn(() => true)

    await wrapper.find('.button--danger').trigger('click')
    await flushPromises()

    expect(store.removeMember).toHaveBeenCalledWith('tenant-1', 'user-2')
  })

  it('calls tenant.grantTenantAdmin for a Normal-tier member', async () => {
    const { wrapper, store } = await mountView()

    const grantButton = wrapper
      .findAll('button')
      .find((b) => b.text() === 'Conceder Tenant Admin')
    await grantButton.trigger('click')
    await flushPromises()

    expect(store.grantTenantAdmin).toHaveBeenCalledWith('tenant-1', 'user-2')
  })

  it('calls tenant.revokeTenantAdmin for a Tenant Admin member', async () => {
    const { wrapper, store } = await mountView()
    store.members = [{ userId: 'user-4', username: 'bruno', isTenantAdmin: true }]
    await wrapper.vm.$nextTick()

    const revokeButton = wrapper
      .findAll('button')
      .find((b) => b.text() === 'Revogar Tenant Admin')
    await revokeButton.trigger('click')
    await flushPromises()

    expect(store.revokeTenantAdmin).toHaveBeenCalledWith('tenant-1', 'user-4')
  })
})
