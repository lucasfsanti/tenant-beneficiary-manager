import { describe, it, expect, vi } from 'vitest'
import pessoaApi from '../../src/services/pessoaApi'

const { apiMock } = vi.hoisted(() => ({
  apiMock: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }
}))
vi.mock('../../src/services/api', () => ({ default: apiMock }))

describe('pessoaApi', () => {
  it('list() GETs /pessoas with the given params', () => {
    pessoaApi.list({ nome: 'ana' })
    expect(apiMock.get).toHaveBeenCalledWith('/pessoas', { params: { nome: 'ana' } })
  })

  it('get() GETs /pessoas/:id', () => {
    pessoaApi.get('p1')
    expect(apiMock.get).toHaveBeenCalledWith('/pessoas/p1')
  })

  it('create() POSTs to /pessoas with the payload', () => {
    const payload = { nome: 'Fulano', cpf: '11111111111' }
    pessoaApi.create(payload)
    expect(apiMock.post).toHaveBeenCalledWith('/pessoas', payload)
  })

  it('update() PUTs to /pessoas/:id with the payload', () => {
    const payload = { nome: 'Fulano' }
    pessoaApi.update('p1', payload)
    expect(apiMock.put).toHaveBeenCalledWith('/pessoas/p1', payload)
  })

  it('remove() DELETEs /pessoas/:id', () => {
    pessoaApi.remove('p1')
    expect(apiMock.delete).toHaveBeenCalledWith('/pessoas/p1')
  })
})
