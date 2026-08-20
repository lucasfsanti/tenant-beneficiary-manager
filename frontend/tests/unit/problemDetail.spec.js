import { describe, it, expect } from 'vitest'
import { extractProblem } from '../../src/services/problemDetail'

describe('extractProblem', () => {
  it('returns the RFC 7807 body when the response carries one', () => {
    const problem = { title: 'Falha de validação', detail: 'CPF inválido' }

    expect(extractProblem({ response: { data: problem } })).toBe(problem)
  })

  it('falls back to a generic message when the response has no data', () => {
    expect(extractProblem({ response: { data: null } })).toEqual({
      title: 'Erro',
      detail: 'Ocorreu um erro inesperado. Tente novamente.'
    })
  })

  it('falls back to a generic message when the response data is not an object', () => {
    expect(extractProblem({ response: { data: 'plain text error' } })).toEqual({
      title: 'Erro',
      detail: 'Ocorreu um erro inesperado. Tente novamente.'
    })
  })

  it('falls back to a generic message for a network error with no response at all', () => {
    expect(extractProblem({ response: undefined })).toEqual({
      title: 'Erro',
      detail: 'Ocorreu um erro inesperado. Tente novamente.'
    })
  })
})
