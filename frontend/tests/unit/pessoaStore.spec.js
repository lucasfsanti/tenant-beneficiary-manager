import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePessoaStore } from '../../src/stores/pessoa'

const { apiMock } = vi.hoisted(() => ({
  apiMock: { list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() }
}))
vi.mock('../../src/services/pessoaApi', () => ({ default: apiMock }))

describe('pessoa store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchList() loads the page into state', async () => {
    apiMock.list.mockResolvedValue({
      data: { content: [{ id: 'p1' }], page: 0, totalPages: 1, totalElements: 1 }
    })
    const store = usePessoaStore()

    await store.fetchList({ nome: 'ana' })

    expect(store.items).toEqual([{ id: 'p1' }])
    expect(store.loading).toBe(false)
  })

  it('fetchList() sets problem on failure', async () => {
    apiMock.list.mockRejectedValue({ response: { data: { detail: 'boom' } } })
    const store = usePessoaStore()

    await store.fetchList()

    expect(store.problem.detail).toBe('boom')
    expect(store.loading).toBe(false)
  })

  it('create() returns true on success', async () => {
    apiMock.create.mockResolvedValue({})
    const store = usePessoaStore()

    expect(await store.create({ nome: 'Fulano' })).toBe(true)
  })

  it('create() sets problem and returns false on failure', async () => {
    apiMock.create.mockRejectedValue({ response: { data: { detail: 'boom' } } })
    const store = usePessoaStore()

    expect(await store.create({ nome: 'Fulano' })).toBe(false)
    expect(store.problem.detail).toBe('boom')
  })

  it('update() returns true on success', async () => {
    apiMock.update.mockResolvedValue({})
    const store = usePessoaStore()

    expect(await store.update('p1', { nome: 'Fulano' })).toBe(true)
  })

  it('update() sets problem and returns false on failure', async () => {
    apiMock.update.mockRejectedValue({ response: { data: { detail: 'boom' } } })
    const store = usePessoaStore()

    expect(await store.update('p1', { nome: 'Fulano' })).toBe(false)
    expect(store.problem.detail).toBe('boom')
  })

  it('remove() returns true on success', async () => {
    apiMock.remove.mockResolvedValue({})
    const store = usePessoaStore()

    expect(await store.remove('p1')).toBe(true)
  })

  it('remove() sets problem and returns false on failure', async () => {
    apiMock.remove.mockRejectedValue({ response: { data: { detail: 'boom' } } })
    const store = usePessoaStore()

    expect(await store.remove('p1')).toBe(false)
    expect(store.problem.detail).toBe('boom')
  })
})
