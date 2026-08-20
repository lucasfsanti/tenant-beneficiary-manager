import { describe, it, expect, vi } from 'vitest'
import beneficiarioApi from '../../src/services/beneficiarioApi'

const { apiMock } = vi.hoisted(() => ({
  apiMock: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }
}))
vi.mock('../../src/services/api', () => ({ default: apiMock }))

describe('beneficiarioApi', () => {
  it('list() GETs /beneficiarios with the given params', () => {
    beneficiarioApi.list({ status: 'ATIVO' })
    expect(apiMock.get).toHaveBeenCalledWith('/beneficiarios', { params: { status: 'ATIVO' } })
  })

  it('get() GETs /beneficiarios/:id', () => {
    beneficiarioApi.get('b1')
    expect(apiMock.get).toHaveBeenCalledWith('/beneficiarios/b1')
  })

  it('create() POSTs to /beneficiarios with the payload', () => {
    const payload = { pessoaId: 'p1', matricula: 'MAT-1' }
    beneficiarioApi.create(payload)
    expect(apiMock.post).toHaveBeenCalledWith('/beneficiarios', payload)
  })

  it('update() PUTs to /beneficiarios/:id with the payload', () => {
    const payload = { matricula: 'MAT-1' }
    beneficiarioApi.update('b1', payload)
    expect(apiMock.put).toHaveBeenCalledWith('/beneficiarios/b1', payload)
  })

  it('remove() DELETEs /beneficiarios/:id', () => {
    beneficiarioApi.remove('b1')
    expect(apiMock.delete).toHaveBeenCalledWith('/beneficiarios/b1')
  })
})
