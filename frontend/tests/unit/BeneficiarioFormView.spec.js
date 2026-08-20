import { describe, it, expect, beforeEach, vi } from 'vitest'
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
    listPessoasMock
      .mockReset()
      .mockResolvedValue({ data: { content: [{ id: 'p1', nome: 'Fulano', cpf: '11111111111' }] } })
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

  async function pickPessoa(wrapper, text = 'ful') {
    await wrapper.find('#pessoaId input').setValue(text)
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()
    await wrapper.find('.searchable-select__option').trigger('mousedown')
  }

  it('shows "Novo Beneficiário" in create mode, with no Pessoa search on mount', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('h1').text()).toBe('Novo Beneficiário')
    expect(listPessoasMock).not.toHaveBeenCalled()
    expect(wrapper.find('#pessoaId input').element.value).toBe('')
  })

  it('typing into the Pessoa field narrows to matching people via pessoaApi.list', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('#pessoaId input').setValue('ful')
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(listPessoasMock).toHaveBeenCalledWith({ nome: 'ful', size: 20 })
    expect(wrapper.find('.searchable-select__option').text()).toBe('Fulano (11111111111)')
    vi.useRealTimers()
  })

  it('selecting a Pessoa sets the form value and shows the selected name', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const wrapper = mountView()
    await flushPromises()

    await pickPessoa(wrapper)

    expect(wrapper.find('#pessoaId input').element.value).toBe('Fulano (11111111111)')
    vi.useRealTimers()
  })

  it('loads and pre-fills the existing record, in edit mode, without a search', async () => {
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
    expect(wrapper.find('#pessoaId input').element.value).toBe('Outra Pessoa')
    expect(listPessoasMock).not.toHaveBeenCalled()
  })

  it('creates a new Beneficiário and navigates back on success', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const wrapper = mountView()
    await flushPromises()

    await pickPessoa(wrapper)
    await wrapper.find('#matricula').setValue('MAT-NEW')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(createMock).toHaveBeenCalledWith(expect.objectContaining({ pessoaId: 'p1' }))
    expect(router.currentRoute.value.path).toBe('/beneficiarios')
    vi.useRealTimers()
  })

  it('does not submit without a Pessoa selected', async () => {
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('#matricula').setValue('MAT-NEW')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(createMock).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/')
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
      expect.objectContaining({ pessoaId: 'p1', matricula: 'MAT-1-CHANGED' })
    )
    expect(router.currentRoute.value.path).toBe('/beneficiarios')
  })

  it('shows the error and stays on the page when saving fails', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
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

    await pickPessoa(wrapper)
    await wrapper.find('#matricula').setValue('MAT-DUP')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(store.create).toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/')
    expect(wrapper.find('.error-banner').exists()).toBe(true)
    vi.useRealTimers()
  })
})
