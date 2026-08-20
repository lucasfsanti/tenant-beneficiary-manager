import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import BeneficiarioFormView from '../../src/views/BeneficiarioFormView.vue'

const getMock = vi.fn()
const createMock = vi.fn()
const listPessoasMock = vi.fn()

vi.mock('../../src/services/beneficiarioApi', () => ({
  default: {
    get: (...args) => getMock(...args),
    create: (...args) => createMock(...args)
  }
}))
vi.mock('../../src/services/pessoaApi', () => ({
  default: {
    list: (...args) => listPessoasMock(...args)
  }
}))

describe('BeneficiarioFormView', () => {
  let router

  beforeEach(async () => {
    setActivePinia(createPinia())
    listPessoasMock.mockReset().mockResolvedValue({ data: { content: [{ id: 'p1', nome: 'Fulano', cpf: '11111111111' }] } })
    getMock.mockReset()
    createMock.mockReset().mockResolvedValue({ data: {} })
    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: { template: '<div />' } },
        { path: '/beneficiarios', component: { template: '<div />' } }
      ]
    })
    await router.push('/')
    await router.isReady()
  })

  function mountView(props = {}) {
    return mount(BeneficiarioFormView, { props, global: { plugins: [router] } })
  }

  it('shows "Novo Beneficiário" and searches Pessoas on mount, in create mode', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('h1').text()).toBe('Novo Beneficiário')
    expect(listPessoasMock).toHaveBeenCalledWith({ nome: '', size: 20 })
    expect(wrapper.findAll('#pessoaId option')).toHaveLength(2) // placeholder + Fulano
  })

  it('loads and pre-fills the existing record, in edit mode', async () => {
    getMock.mockResolvedValue({
      data: {
        pessoaId: 'p2',
        pessoaNome: 'Outra Pessoa',
        matricula: 'MAT-9',
        tipo: 'DEPENDENTE',
        status: 'INATIVO',
        dataAdesao: '2026-01-01'
      }
    })

    const wrapper = mountView({ id: 'b1' })
    await flushPromises()

    expect(wrapper.find('h1').text()).toBe('Editar Beneficiário')
    expect(getMock).toHaveBeenCalledWith('b1')
    expect(wrapper.find('#matricula').element.value).toBe('MAT-9')
    expect(wrapper.find('#tipo').element.value).toBe('DEPENDENTE')
    // the edited record's Pessoa (p2) isn't among the search results (only p1 is) — it must be
    // prepended so the <select> can show it as selected.
    expect(wrapper.find('#pessoaId').element.value).toBe('p2')
    expect(wrapper.text()).toContain('Outra Pessoa')
  })

  it("doesn't duplicate the Pessoa option when it's already among the search results", async () => {
    getMock.mockResolvedValue({
      data: {
        pessoaId: 'p1',
        pessoaNome: 'Fulano',
        matricula: 'MAT-1',
        tipo: 'TITULAR',
        status: 'ATIVO',
        dataAdesao: null
      }
    })

    const wrapper = mountView({ id: 'b1' })
    await flushPromises()

    expect(wrapper.findAll('#pessoaId option')).toHaveLength(2) // placeholder + Fulano, no dupe
  })

  it('creates a new Beneficiário and navigates back on success', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('#pessoaId').setValue('p1')
    await wrapper.find('#matricula').setValue('MAT-NEW')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/beneficiarios')
  })

  it('updates an existing Beneficiário and navigates back on success, in edit mode', async () => {
    getMock.mockResolvedValue({
      data: {
        pessoaId: 'p1',
        pessoaNome: 'Fulano',
        matricula: 'MAT-1',
        tipo: 'TITULAR',
        status: 'ATIVO',
        dataAdesao: null
      }
    })
    const { useBeneficiarioStore } = await import('../../src/stores/beneficiario')
    const store = useBeneficiarioStore()
    store.update = vi.fn(async () => true)

    const wrapper = mountView({ id: 'b1' })
    await flushPromises()

    await wrapper.find('#matricula').setValue('MAT-1-CHANGED')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(store.update).toHaveBeenCalledWith(
      'b1',
      expect.objectContaining({ matricula: 'MAT-1-CHANGED' })
    )
    expect(router.currentRoute.value.path).toBe('/beneficiarios')
  })

  it('shows the error and stays on the page when saving fails', async () => {
    const wrapper = mountView()
    await flushPromises()
    // Route the store's create() to fail by leaving pessoaId blank isn't representative of a
    // server error — instead exercise the store directly via its own problem state, matching
    // how the other views' error paths are tested: force the API call itself to reject.
    const { useBeneficiarioStore } = await import('../../src/stores/beneficiario')
    const store = useBeneficiarioStore()
    store.create = vi.fn(async () => {
      store.problem = { title: 'Conflito', detail: 'Matrícula já cadastrada.' }
      return false
    })

    await wrapper.find('#pessoaId').setValue('p1')
    await wrapper.find('#matricula').setValue('MAT-DUP')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(store.create).toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/')
    expect(wrapper.find('.error-banner').exists()).toBe(true)
  })

  describe('with fake timers', () => {
    beforeEach(() => {
      vi.useFakeTimers({ shouldAdvanceTime: true })
    })

    afterEach(() => {
      vi.useRealTimers()
    })

    it('debounces the Pessoa search input', async () => {
      const wrapper = mountView()
      await flushPromises()
      listPessoasMock.mockClear()

      await wrapper.find('#pessoaBusca').setValue('ana')
      expect(listPessoasMock).not.toHaveBeenCalled()

      await vi.advanceTimersByTimeAsync(300)
      await flushPromises()

      expect(listPessoasMock).toHaveBeenCalledWith({ nome: 'ana', size: 20 })
    })
  })
})
