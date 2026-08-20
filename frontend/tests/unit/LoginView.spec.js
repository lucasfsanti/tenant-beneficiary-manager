import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import LoginView from '../../src/views/LoginView.vue'
import { useAuthStore } from '../../src/stores/auth'

describe('LoginView', () => {
  let router

  beforeEach(async () => {
    setActivePinia(createPinia())
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/login', component: { template: '<div />' } },
        { path: '/pessoas', component: { template: '<div />' } },
        { path: '/beneficiarios', component: { template: '<div />' } }
      ]
    })
  })

  async function mountAt(path) {
    await router.push(path)
    await router.isReady()
    return mount(LoginView, { global: { plugins: [router] } })
  }

  it('logs in and navigates to /pessoas by default', async () => {
    const auth = useAuthStore()
    auth.login = vi.fn(async () => true)
    const wrapper = await mountAt('/login')

    await wrapper.find('#username').setValue('ana')
    await wrapper.find('#password').setValue('demo123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(auth.login).toHaveBeenCalledWith('ana', 'demo123')
    expect(router.currentRoute.value.path).toBe('/pessoas')
  })

  it('navigates to the redirect query param when present', async () => {
    const auth = useAuthStore()
    auth.login = vi.fn(async () => true)
    const wrapper = await mountAt('/login?redirect=/beneficiarios')

    await wrapper.find('#username').setValue('ana')
    await wrapper.find('#password').setValue('demo123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/beneficiarios')
  })

  it('shows the error and stays on the page when login fails', async () => {
    const auth = useAuthStore()
    auth.login = vi.fn(async () => {
      auth.error = 'Usuário ou senha inválidos.'
      return false
    })
    const wrapper = await mountAt('/login')

    await wrapper.find('#username').setValue('ana')
    await wrapper.find('#password').setValue('errada')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.find('.error-banner').exists()).toBe(true)
    expect(wrapper.text()).toContain('Usuário ou senha inválidos.')
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
