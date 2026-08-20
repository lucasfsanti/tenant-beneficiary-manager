import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useBeneficiarioStore } from '../../src/stores/beneficiario'

const { apiMock } = vi.hoisted(() => ({
  apiMock: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() }
}))
vi.mock('../../src/services/beneficiarioApi', () => ({ default: apiMock }))

describe('beneficiario store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchList() loads the page into state', async () => {
    apiMock.list.mockResolvedValue({
      data: { content: [{ id: 'b1' }], page: 0, totalPages: 1, totalElements: 1 }
    })
    const store = useBeneficiarioStore()

    await store.fetchList({ pessoaNome: 'ana', status: 'ATIVO' })

    expect(store.items).toEqual([{ id: 'b1' }])
    expect(store.loading).toBe(false)
    expect(store.lastFilters).toEqual({ pessoaNome: 'ana', status: 'ATIVO', page: 0, size: 20 })
  })

  it('fetchList() sets problem on failure', async () => {
    apiMock.list.mockRejectedValue({ response: { data: { detail: 'boom' } } })
    const store = useBeneficiarioStore()

    await store.fetchList()

    expect(store.problem.detail).toBe('boom')
    expect(store.loading).toBe(false)
  })

  it('refetch() re-runs fetchList with the last filters used', async () => {
    apiMock.list.mockResolvedValue({
      data: { content: [], page: 0, totalPages: 0, totalElements: 0 }
    })
    const store = useBeneficiarioStore()
    await store.fetchList({ pessoaNome: 'ana' })
    apiMock.list.mockClear()

    await store.refetch()

    expect(apiMock.list).toHaveBeenCalledWith(
      expect.objectContaining({ pessoaNome: 'ana' })
    )
  })

  it('clear() resets the list state', () => {
    const store = useBeneficiarioStore()
    store.items = [{ id: 'b1' }]
    store.page = 2
    store.totalPages = 5
    store.totalElements = 42

    store.clear()

    expect(store.items).toEqual([])
    expect(store.page).toBe(0)
    expect(store.totalPages).toBe(0)
    expect(store.totalElements).toBe(0)
  })

  it('create() returns true on success', async () => {
    apiMock.create.mockResolvedValue({})
    const store = useBeneficiarioStore()

    expect(await store.create({ pessoaId: 'p1' })).toBe(true)
  })

  it('create() sets problem and returns false on failure', async () => {
    apiMock.create.mockRejectedValue({ response: { data: { detail: 'boom' } } })
    const store = useBeneficiarioStore()

    expect(await store.create({ pessoaId: 'p1' })).toBe(false)
    expect(store.problem.detail).toBe('boom')
  })

  it('update() returns true on success', async () => {
    apiMock.update.mockResolvedValue({})
    const store = useBeneficiarioStore()

    expect(await store.update('b1', { matricula: 'X' })).toBe(true)
  })

  it('update() sets problem and returns false on failure', async () => {
    apiMock.update.mockRejectedValue({ response: { data: { detail: 'boom' } } })
    const store = useBeneficiarioStore()

    expect(await store.update('b1', { matricula: 'X' })).toBe(false)
    expect(store.problem.detail).toBe('boom')
  })

  it('remove() returns true on success', async () => {
    apiMock.remove.mockResolvedValue({})
    const store = useBeneficiarioStore()

    expect(await store.remove('b1')).toBe(true)
  })

  it('remove() sets problem and returns false on failure', async () => {
    apiMock.remove.mockRejectedValue({ response: { data: { detail: 'boom' } } })
    const store = useBeneficiarioStore()

    expect(await store.remove('b1')).toBe(false)
    expect(store.problem.detail).toBe('boom')
  })
})
