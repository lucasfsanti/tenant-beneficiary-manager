import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PessoaListView from '../../src/views/PessoaListView.vue'

const listMock = vi.fn()
const removeMock = vi.fn()

vi.mock('../../src/services/pessoaApi', () => ({
  default: {
    list: (...args) => listMock(...args),
    remove: (...args) => removeMock(...args)
  }
}))

function page(items) {
  return { data: { content: items, page: 0, totalPages: 1, totalElements: items.length } }
}

describe('PessoaListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    listMock.mockReset()
    removeMock.mockReset()
  })

  it('shows the empty state when there are no results', async () => {
    listMock.mockResolvedValue(page([]))
    const wrapper = mount(PessoaListView)
    await flushPromises()

    expect(wrapper.text()).toContain('Nenhuma pessoa encontrada.')
  })

  it('renders each pessoa row, with an em-dash for a missing email', async () => {
    listMock.mockResolvedValue(
      page([
        { id: 'p1', nome: 'Fulano', cpf: '11111111111', email: 'fulano@example.com' },
        { id: 'p2', nome: 'Ciclana', cpf: '22222222222', email: null }
      ])
    )
    const wrapper = mount(PessoaListView)
    await flushPromises()

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('fulano@example.com')
    expect(rows[1].text()).toContain('—')
  })

  it('shows the ErrorBanner when the store reports a problem', async () => {
    listMock.mockRejectedValue({ response: { data: { title: 'Erro' } } })
    const wrapper = mount(PessoaListView)
    await flushPromises()

    expect(wrapper.find('.error-banner').exists()).toBe(true)
  })

  it('deletes a pessoa after confirmation and reloads the list', async () => {
    listMock.mockResolvedValue(
      page([{ id: 'p1', nome: 'Fulano', cpf: '11111111111', email: null }])
    )
    removeMock.mockResolvedValue({})
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = mount(PessoaListView)
    await flushPromises()

    await wrapper.find('button.button--danger').trigger('click')
    await flushPromises()

    expect(removeMock).toHaveBeenCalledWith('p1')
    expect(listMock).toHaveBeenCalledTimes(2)
  })

  it('does not delete when the confirmation is declined', async () => {
    listMock.mockResolvedValue(
      page([{ id: 'p1', nome: 'Fulano', cpf: '11111111111', email: null }])
    )
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const wrapper = mount(PessoaListView)
    await flushPromises()

    await wrapper.find('button.button--danger').trigger('click')
    await flushPromises()

    expect(removeMock).not.toHaveBeenCalled()
  })

  it('does not reload the list when the delete itself fails', async () => {
    listMock.mockResolvedValue(
      page([{ id: 'p1', nome: 'Fulano', cpf: '11111111111', email: null }])
    )
    removeMock.mockRejectedValue({ response: { data: {} } })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = mount(PessoaListView)
    await flushPromises()

    await wrapper.find('button.button--danger').trigger('click')
    await flushPromises()

    expect(listMock).toHaveBeenCalledTimes(1)
  })

  describe('with fake timers', () => {
    beforeEach(() => {
      vi.useFakeTimers({ shouldAdvanceTime: true })
    })

    afterEach(() => {
      vi.useRealTimers()
    })

    it('debounces the name filter before re-fetching', async () => {
      listMock.mockResolvedValue(page([]))
      const wrapper = mount(PessoaListView)
      await flushPromises()
      listMock.mockClear()

      await wrapper.find('.filter-input').setValue('ana')
      expect(listMock).not.toHaveBeenCalled()

      await vi.advanceTimersByTimeAsync(300)
      await flushPromises()

      expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ nome: 'ana' }))
    })
  })
})
