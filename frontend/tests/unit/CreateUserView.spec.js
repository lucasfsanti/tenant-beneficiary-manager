import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import CreateUserView from '../../src/views/CreateUserView.vue'
import { useAuthStore } from '../../src/stores/auth'

describe('CreateUserView', () => {
  let router

  beforeEach(async () => {
    setActivePinia(createPinia())
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/login', name: 'login', component: { template: '<div />' } }
      ]
    })
    await router.push('/')
    await router.isReady()
  })

  function mountView() {
    return mount(CreateUserView, { global: { plugins: [router] } })
  }

  it('calls auth.register with the submitted username and password', async () => {
    const auth = useAuthStore()
    auth.register = vi.fn(() => Promise.resolve(true))
    const wrapper = mountView()

    await wrapper.find('#username').setValue('novo-usuario')
    await wrapper.find('#password').setValue('senha-segura')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(auth.register).toHaveBeenCalledWith('novo-usuario', 'senha-segura')
  })

  it('navigates to /login after a successful registration', async () => {
    const auth = useAuthStore()
    auth.register = vi.fn(() => Promise.resolve(true))
    const wrapper = mountView()

    await wrapper.find('#username').setValue('novo-usuario')
    await wrapper.find('#password').setValue('senha-segura')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('shows the store error and does not navigate when registration fails', async () => {
    const auth = useAuthStore()
    auth.register = vi.fn(() => {
      auth.error = 'Nome de usuário já cadastrado.'
      return Promise.resolve(false)
    })
    const wrapper = mountView()

    await wrapper.find('#username').setValue('ja-existe')
    await wrapper.find('#password').setValue('senha-segura')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.find('.error-banner').exists()).toBe(true)
    expect(wrapper.text()).toContain('Nome de usuário já cadastrado.')
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('renders no role-selection control of any kind', () => {
    const wrapper = mountView()

    expect(wrapper.find('select').exists()).toBe(false)
    expect(wrapper.findAll('input[type="radio"]')).toHaveLength(0)
    expect(wrapper.findAll('input[type="checkbox"]')).toHaveLength(0)
  })
})
