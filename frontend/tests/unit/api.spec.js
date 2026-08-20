import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../../src/stores/auth'

const { requestHandlers, responseHandlers } = vi.hoisted(() => ({
  requestHandlers: [],
  responseHandlers: []
}))

vi.mock('axios', () => ({
  default: {
    create: () => ({
      interceptors: {
        request: { use: (fulfilled) => requestHandlers.push(fulfilled) },
        response: { use: (fulfilled, rejected) => responseHandlers.push({ fulfilled, rejected }) }
      }
    })
  }
}))

import '../../src/services/api'

describe('api interceptors', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('attaches Authorization and X-Tenant-Id when both are present', () => {
    const auth = useAuthStore()
    auth.token = 'abc'
    auth.activeTenantId = 'tenant-a'

    const result = requestHandlers[0]({ headers: {} })

    expect(result.headers.Authorization).toBe('Bearer abc')
    expect(result.headers['X-Tenant-Id']).toBe('tenant-a')
  })

  it('omits both headers when there is no token or active tenant', () => {
    const result = requestHandlers[0]({ headers: {} })

    expect(result.headers.Authorization).toBeUndefined()
    expect(result.headers['X-Tenant-Id']).toBeUndefined()
  })

  it('passes a successful response through unchanged', () => {
    const response = { data: { ok: true } }

    expect(responseHandlers[0].fulfilled(response)).toBe(response)
  })

  it('logs out and rejects on a 401 response', async () => {
    const auth = useAuthStore()
    auth.token = 'abc'
    auth.logout = vi.fn()

    await expect(
      responseHandlers[0].rejected({ response: { status: 401 } })
    ).rejects.toBeDefined()
    expect(auth.logout).toHaveBeenCalled()
  })

  it('does not log out on a non-401 error', async () => {
    const auth = useAuthStore()
    auth.logout = vi.fn()

    await expect(
      responseHandlers[0].rejected({ response: { status: 500 } })
    ).rejects.toBeDefined()
    expect(auth.logout).not.toHaveBeenCalled()
  })

  it('does not log out on a network error with no response at all', async () => {
    const auth = useAuthStore()
    auth.logout = vi.fn()

    await expect(responseHandlers[0].rejected({ response: undefined })).rejects.toBeDefined()
    expect(auth.logout).not.toHaveBeenCalled()
  })
})
