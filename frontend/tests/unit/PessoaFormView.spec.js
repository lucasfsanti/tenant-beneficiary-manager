import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import PessoaFormView from '../../src/views/PessoaFormView.vue'
import { usePessoaStore } from '../../src/stores/pessoa'

const getMock = vi.fn()

vi.mock('../../src/services/pessoaApi', () => ({
  default: {
    get: (...args) => getMock(...args)
  }
}))

describe('PessoaFormView', () => {
  let router

  beforeEach(async () => {
    setActivePinia(createPinia())
    getMock.mockReset()
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/pessoas', component: { template: '<div />' } }
      ]
    })
    await router.push('/')
    await router.isReady()
  })

  function mountView(props = {}) {
    return mount(PessoaFormView, { props, global: { plugins: [router] } })
  }

  it('shows "Nova Pessoa" and does not fetch anything, in create mode', () => {
    const wrapper = mountView()

    expect(wrapper.find('h1').text()).toBe('Nova Pessoa')
    expect(getMock).not.toHaveBeenCalled()
  })

  it('loads and pre-fills the existing record, in edit mode', async () => {
    getMock.mockResolvedValue({
      data: { nome: 'Fulano', cpf: '11111111111', dataNascimento: '1990-01-01', email: 'f@example.com' }
    })

    const wrapper = mountView({ id: 'p1' })
    await flushPromises()

    expect(wrapper.find('h1').text()).toBe('Editar Pessoa')
    expect(getMock).toHaveBeenCalledWith('p1')
    expect(wrapper.find('#nome').element.value).toBe('Fulano')
    expect(wrapper.find('#cpf').element.value).toBe('11111111111')
    expect(wrapper.find('#email').element.value).toBe('f@example.com')
  })

  it('defaults optional fields to empty when the record has none', async () => {
    getMock.mockResolvedValue({ data: { nome: 'Fulano', cpf: '11111111111', dataNascimento: null, email: null } })

    const wrapper = mountView({ id: 'p1' })
    await flushPromises()

    expect(wrapper.find('#dataNascimento').element.value).toBe('')
    expect(wrapper.find('#email').element.value).toBe('')
  })

  it('creates a new Pessoa and navigates back on success', async () => {
    const store = usePessoaStore()
    store.create = vi.fn(async () => true)
    const wrapper = mountView()

    await wrapper.find('#nome').setValue('Novo')
    await wrapper.find('#cpf').setValue('22222222222')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(store.create).toHaveBeenCalledWith(
      expect.objectContaining({ nome: 'Novo', cpf: '22222222222', dataNascimento: null, email: null })
    )
    expect(router.currentRoute.value.path).toBe('/pessoas')
  })

  it('updates an existing Pessoa and navigates back on success, in edit mode', async () => {
    getMock.mockResolvedValue({ data: { nome: 'Fulano', cpf: '11111111111', dataNascimento: null, email: null } })
    const store = usePessoaStore()
    store.update = vi.fn(async () => true)

    const wrapper = mountView({ id: 'p1' })
    await flushPromises()
    await wrapper.find('#nome').setValue('Fulano Alterado')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(store.update).toHaveBeenCalledWith(
      'p1',
      expect.objectContaining({ nome: 'Fulano Alterado' })
    )
    expect(router.currentRoute.value.path).toBe('/pessoas')
  })

  it('shows the error and stays on the page when saving fails', async () => {
    const store = usePessoaStore()
    store.create = vi.fn(async () => {
      store.problem = { title: 'Conflito', detail: 'CPF já cadastrado.' }
      return false
    })
    const wrapper = mountView()

    await wrapper.find('#nome').setValue('Novo')
    await wrapper.find('#cpf').setValue('33333333333')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/')
    expect(wrapper.find('.error-banner').exists()).toBe(true)
  })
})
