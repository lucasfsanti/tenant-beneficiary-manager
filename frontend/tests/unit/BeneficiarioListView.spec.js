import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import BeneficiarioListView from '../../src/views/BeneficiarioListView.vue'
import { useAuthStore } from '../../src/stores/auth'

const listMock = vi.fn()
const removeMock = vi.fn()

vi.mock('../../src/services/beneficiarioApi', () => ({
  default: {
    list: (...args) => listMock(...args),
    remove: (...args) => removeMock(...args)
  }
}))

function page(items, overrides = {}) {
  return { data: { content: items, page: 0, totalPages: 1, totalElements: items.length, ...overrides } }
}

describe('BeneficiarioListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    listMock.mockReset()
    removeMock.mockReset()
  })

  it('shows the empty state when there are no results', async () => {
    listMock.mockResolvedValue(page([]))
    const wrapper = mount(BeneficiarioListView)
    await flushPromises()

    expect(wrapper.text()).toContain('Nenhum beneficiário encontrado')
  })

  it('renders each beneficiário row', async () => {
    listMock.mockResolvedValue(
      page([
        {
          id: 'b1',
          pessoaNome: 'Fulano',
          matricula: 'MAT-1',
          tipo: 'TITULAR',
          status: 'ATIVO'
        },
        {
          id: 'b2',
          pessoaNome: 'Ciclana',
          matricula: 'MAT-2',
          tipo: 'DEPENDENTE',
          status: 'INATIVO'
        }
      ])
    )
    const wrapper = mount(BeneficiarioListView)
    await flushPromises()

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('Fulano')
    expect(rows[0].text()).toContain('Titular')
    expect(rows[0].text()).toContain('Ativo')
    expect(rows[1].text()).toContain('Dependente')
    expect(rows[1].text()).toContain('Inativo')
  })

  it('shows the ErrorBanner when the store reports a problem', async () => {
    listMock.mockRejectedValue({ response: { data: { title: 'Erro' } } })
    const wrapper = mount(BeneficiarioListView)
    await flushPromises()

    expect(wrapper.find('.error-banner').exists()).toBe(true)
  })

  it('deletes a beneficiário after confirmation and reloads the list', async () => {
    listMock.mockResolvedValue(
      page([{ id: 'b1', pessoaNome: 'Fulano', matricula: 'MAT-1', tipo: 'TITULAR', status: 'ATIVO' }])
    )
    removeMock.mockResolvedValue({})
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = mount(BeneficiarioListView)
    await flushPromises()

    await wrapper.find('button.button--danger').trigger('click')
    await flushPromises()

    expect(removeMock).toHaveBeenCalledWith('b1')
    expect(listMock).toHaveBeenCalledTimes(2)
  })

  it('does not delete when the confirmation is declined', async () => {
    listMock.mockResolvedValue(
      page([{ id: 'b1', pessoaNome: 'Fulano', matricula: 'MAT-1', tipo: 'TITULAR', status: 'ATIVO' }])
    )
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const wrapper = mount(BeneficiarioListView)
    await flushPromises()

    await wrapper.find('button.button--danger').trigger('click')
    await flushPromises()

    expect(removeMock).not.toHaveBeenCalled()
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
      const wrapper = mount(BeneficiarioListView)
      await flushPromises()
      listMock.mockClear()

      await wrapper.find('.filter-input').setValue('ana')
      expect(listMock).not.toHaveBeenCalled()

      await vi.advanceTimersByTimeAsync(300)
      await flushPromises()

      expect(listMock).toHaveBeenCalledWith(
        expect.objectContaining({ pessoaNome: 'ana', page: 0 })
      )
    })

    it('re-fetches immediately when the status filter changes', async () => {
      listMock.mockResolvedValue(page([]))
      const wrapper = mount(BeneficiarioListView)
      await flushPromises()
      listMock.mockClear()

      await wrapper.find('select').setValue('ATIVO')
      await vi.advanceTimersByTimeAsync(300)
      await flushPromises()

      expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ status: 'ATIVO' }))
    })
  })

  it('reloads from a clean state when the active tenant changes', async () => {
    listMock.mockResolvedValue(page([]))
    const auth = useAuthStore()
    auth.activeTenantId = 'tenant-a'
    mount(BeneficiarioListView)
    await flushPromises()
    listMock.mockClear()

    auth.activeTenantId = 'tenant-b'
    await flushPromises()

    expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ page: 0 }))
  })

  it('paginates via the PaginationControl', async () => {
    listMock.mockResolvedValue(
      page([{ id: 'b1', pessoaNome: 'Fulano', matricula: 'MAT-1', tipo: 'TITULAR', status: 'ATIVO' }], {
        totalPages: 2
      })
    )
    const wrapper = mount(BeneficiarioListView)
    await flushPromises()
    listMock.mockClear()

    await wrapper.findAll('.pagination-control button')[1].trigger('click')
    await flushPromises()

    expect(listMock).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }))
  })
})
