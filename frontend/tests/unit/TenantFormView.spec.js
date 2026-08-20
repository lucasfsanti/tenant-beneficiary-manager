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

  it('calls tenant.addMember with the specifically selected user, out of several matches', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const { wrapper, store } = await mountView()
    store.searchUser = vi.fn(() =>
      Promise.resolve([
        { id: 'user-3', username: 'carla' },
        { id: 'user-5', username: 'carlos' }
      ])
    )

    await wrapper.find('.add-member-form input').setValue('carl')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    const options = wrapper.findAll('.searchable-select__option')
    expect(options).toHaveLength(2)
    await options[1].trigger('mousedown')
    await wrapper.find('.add-member-form').trigger('submit.prevent')
    await flushPromises()

    expect(store.searchUser).toHaveBeenCalledWith('carl')
    expect(store.addMember).toHaveBeenCalledWith('tenant-1', 'user-5')
    vi.useRealTimers()
  })

  it('does not call tenant.addMember when submitted without picking a match', async () => {
    const { wrapper, store } = await mountView()

    await wrapper.find('.add-member-form').trigger('submit.prevent')
    await flushPromises()

    expect(store.addMember).not.toHaveBeenCalled()
  })

  it('does not call tenant.searchUser for a query shorter than 2 characters', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const { wrapper, store } = await mountView()

    await wrapper.find('.add-member-form input').setValue('c')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(store.searchUser).not.toHaveBeenCalled()
    vi.useRealTimers()
  })

  it('disables the Adicionar button until a match is selected', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const { wrapper, store } = await mountView()
    store.searchUser = vi.fn(() => Promise.resolve([]))

    expect(
      wrapper.find('.add-member-form button[type="submit"]').attributes('disabled')
    ).toBeDefined()

    await wrapper.find('.add-member-form input').setValue('ninguem')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(wrapper.text()).toContain('Nenhum resultado encontrado.')
    expect(
      wrapper.find('.add-member-form button[type="submit"]').attributes('disabled')
    ).toBeDefined()
    vi.useRealTimers()
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

  it('resets the add-member field after a successful add', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const { wrapper } = await mountView()

    await wrapper.find('.add-member-form input').setValue('carla')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    await wrapper.find('.searchable-select__option').trigger('mousedown')
    await wrapper.find('.add-member-form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.find('.add-member-form input').element.value).toBe('')
    vi.useRealTimers()
  })

  it('does not remove a member when the removal confirmation is declined', async () => {
    const { wrapper, store } = await mountView()
    window.confirm = vi.fn(() => false)

    await wrapper.find('.button--danger').trigger('click')
    await flushPromises()

    expect(store.removeMember).not.toHaveBeenCalled()
  })
})

describe('TenantFormView submit', () => {
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

  it('creates a new tenant and navigates to /tenants on success', async () => {
    const auth = useAuthStore()
    auth.user = { id: 'admin-1', isSystemAdmin: true, tenants: [] }
    const wrapper = mount(TenantFormView, { global: { plugins: [router] } })
    const store = useTenantStore()
    store.create = vi.fn(() => Promise.resolve(true))

    await wrapper.find('#name').setValue('Tenant Novo')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(store.create).toHaveBeenCalledWith({ name: 'Tenant Novo' })
    expect(router.currentRoute.value.path).toBe('/tenants')
  })

  it('does not navigate when creating a tenant fails', async () => {
    const auth = useAuthStore()
    auth.user = { id: 'admin-1', isSystemAdmin: true, tenants: [] }
    const wrapper = mount(TenantFormView, { global: { plugins: [router] } })
    const store = useTenantStore()
    store.create = vi.fn(() => Promise.resolve(false))

    await wrapper.find('#name').setValue('Tenant Novo')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/')
  })

  it('updates an existing tenant and navigates on success', async () => {
    const auth = useAuthStore()
    auth.user = { id: 'admin-1', isSystemAdmin: true, tenants: [] }
    vi.mocked((await import('../../src/services/tenantAdminApi')).default.getTenant).mockResolvedValue(
      { data: { id: 'tenant-1', name: 'Tenant Alfa' } }
    )
    const wrapper = mount(TenantFormView, {
      props: { id: 'tenant-1' },
      global: { plugins: [router] }
    })
    await flushPromises()
    const store = useTenantStore()
    store.update = vi.fn(() => Promise.resolve(true))

    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(store.update).toHaveBeenCalledWith('tenant-1', { name: 'Tenant Alfa' })
    expect(router.currentRoute.value.path).toBe('/tenants')
  })

  it('sends a non-system-admin Tenant Admin back to /pessoas on cancel', async () => {
    const auth = useAuthStore()
    auth.user = { id: 'user-1', tenants: [{ id: 'tenant-1', isTenantAdmin: true }] }
    const wrapper = mount(TenantFormView, {
      props: { id: 'tenant-1' },
      global: { plugins: [router] }
    })
    await flushPromises()

    expect(wrapper.find('.button--secondary').attributes('href')).toBe('/pessoas')
  })
})
