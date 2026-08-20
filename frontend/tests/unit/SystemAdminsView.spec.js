import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SystemAdminsView from '../../src/views/SystemAdminsView.vue'
import { useTenantStore } from '../../src/stores/tenant'

describe('SystemAdminsView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  async function search(wrapper, username) {
    await wrapper.find('input').setValue(username)
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()
  }

  it('renders a row per matching user', async () => {
    const store = useTenantStore()
    store.searchUser = vi.fn(async () => [
      { id: 'u1', username: 'ana' },
      { id: 'u2', username: 'ana2' }
    ])
    const wrapper = mount(SystemAdminsView)

    await search(wrapper, 'ana')

    expect(store.searchUser).toHaveBeenCalledWith('ana')
    expect(wrapper.findAll('tbody tr')).toHaveLength(2)
  })

  it('shows the empty state when no user matches', async () => {
    const store = useTenantStore()
    store.searchUser = vi.fn(async () => [])
    const wrapper = mount(SystemAdminsView)

    await search(wrapper, 'ninguem')

    expect(wrapper.find('.empty-state').text()).toContain('Nenhum usuário encontrado')
  })

  it('does not show the empty state before any search has been made', () => {
    const wrapper = mount(SystemAdminsView)

    expect(wrapper.find('.empty-state').exists()).toBe(false)
  })

  it('grants System Admin and shows a confirmation message', async () => {
    const store = useTenantStore()
    store.searchUser = vi.fn(async () => [{ id: 'u1', username: 'ana' }])
    store.grantSystemAdmin = vi.fn(async () => true)
    const wrapper = mount(SystemAdminsView)
    await search(wrapper, 'ana')

    await wrapper.find('tbody button.button').trigger('click')
    await flushPromises()

    expect(store.grantSystemAdmin).toHaveBeenCalledWith('u1')
    expect(wrapper.find('.status-message').text()).toContain('agora é System Admin')
  })

  it('does not show a confirmation message when granting fails', async () => {
    const store = useTenantStore()
    store.searchUser = vi.fn(async () => [{ id: 'u1', username: 'ana' }])
    store.grantSystemAdmin = vi.fn(async () => false)
    const wrapper = mount(SystemAdminsView)
    await search(wrapper, 'ana')

    await wrapper.find('tbody button.button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.status-message').exists()).toBe(false)
  })

  it('revokes System Admin and shows a confirmation message', async () => {
    const store = useTenantStore()
    store.searchUser = vi.fn(async () => [{ id: 'u1', username: 'ana' }])
    store.revokeSystemAdmin = vi.fn(async () => true)
    const wrapper = mount(SystemAdminsView)
    await search(wrapper, 'ana')

    await wrapper.find('tbody button.button--danger').trigger('click')
    await flushPromises()

    expect(store.revokeSystemAdmin).toHaveBeenCalledWith('u1')
    expect(wrapper.find('.status-message').text()).toContain('foi revogado')
  })

  it('does not show a confirmation message when revoking fails', async () => {
    const store = useTenantStore()
    store.searchUser = vi.fn(async () => [{ id: 'u1', username: 'ana' }])
    store.revokeSystemAdmin = vi.fn(async () => false)
    const wrapper = mount(SystemAdminsView)
    await search(wrapper, 'ana')

    await wrapper.find('tbody button.button--danger').trigger('click')
    await flushPromises()

    expect(wrapper.find('.status-message').exists()).toBe(false)
  })

  it('shows the ErrorBanner when the store reports a problem', async () => {
    const store = useTenantStore()
    store.searchUser = vi.fn(async () => {
      store.problem = { title: 'Erro' }
      return []
    })
    const wrapper = mount(SystemAdminsView)

    await search(wrapper, 'ana')

    expect(wrapper.find('.error-banner').exists()).toBe(true)
  })
})
