import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useTenantStore } from '../../src/stores/tenant'

const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    listTenants: vi.fn(),
    createTenant: vi.fn(),
    updateTenant: vi.fn(),
    removeTenant: vi.fn(),
    grantSystemAdmin: vi.fn(),
    revokeSystemAdmin: vi.fn(),
    searchUsers: vi.fn(),
    listMembers: vi.fn(),
    addMember: vi.fn(),
    removeMember: vi.fn(),
    grantTenantAdmin: vi.fn(),
    revokeTenantAdmin: vi.fn()
  }
}))
vi.mock('../../src/services/tenantAdminApi', () => ({ default: apiMock }))

const failure = { response: { data: { detail: 'boom' } } }

describe('tenant store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchList() loads tenants into state', async () => {
    apiMock.listTenants.mockResolvedValue({ data: [{ id: 't1' }] })
    const store = useTenantStore()

    await store.fetchList()

    expect(store.items).toEqual([{ id: 't1' }])
    expect(store.loading).toBe(false)
  })

  it('fetchList() sets problem on failure', async () => {
    apiMock.listTenants.mockRejectedValue(failure)
    const store = useTenantStore()

    await store.fetchList()

    expect(store.problem.detail).toBe('boom')
    expect(store.loading).toBe(false)
  })

  it('create() returns true on success and false with problem on failure', async () => {
    apiMock.createTenant.mockResolvedValueOnce({})
    const store = useTenantStore()
    expect(await store.create({ name: 'Tenant Novo' })).toBe(true)

    apiMock.createTenant.mockRejectedValueOnce(failure)
    expect(await store.create({ name: 'Tenant Novo' })).toBe(false)
    expect(store.problem.detail).toBe('boom')
  })

  it('update() returns true on success and false with problem on failure', async () => {
    apiMock.updateTenant.mockResolvedValueOnce({})
    const store = useTenantStore()
    expect(await store.update('t1', { name: 'Renomeado' })).toBe(true)

    apiMock.updateTenant.mockRejectedValueOnce(failure)
    expect(await store.update('t1', { name: 'Renomeado' })).toBe(false)
    expect(store.problem.detail).toBe('boom')
  })

  it('remove() returns true on success and false with problem on failure', async () => {
    apiMock.removeTenant.mockResolvedValueOnce({})
    const store = useTenantStore()
    expect(await store.remove('t1')).toBe(true)

    apiMock.removeTenant.mockRejectedValueOnce(failure)
    expect(await store.remove('t1')).toBe(false)
    expect(store.problem.detail).toBe('boom')
  })

  it('grantSystemAdmin() returns true on success and false with problem on failure', async () => {
    apiMock.grantSystemAdmin.mockResolvedValueOnce({})
    const store = useTenantStore()
    expect(await store.grantSystemAdmin('u1')).toBe(true)

    apiMock.grantSystemAdmin.mockRejectedValueOnce(failure)
    expect(await store.grantSystemAdmin('u1')).toBe(false)
    expect(store.problem.detail).toBe('boom')
  })

  it('revokeSystemAdmin() returns true on success and false with problem on failure', async () => {
    apiMock.revokeSystemAdmin.mockResolvedValueOnce({})
    const store = useTenantStore()
    expect(await store.revokeSystemAdmin('u1')).toBe(true)

    apiMock.revokeSystemAdmin.mockRejectedValueOnce(failure)
    expect(await store.revokeSystemAdmin('u1')).toBe(false)
    expect(store.problem.detail).toBe('boom')
  })

  it('searchUser() returns the results on success and [] with problem on failure', async () => {
    apiMock.searchUsers.mockResolvedValueOnce({ data: [{ id: 'u1' }] })
    const store = useTenantStore()
    expect(await store.searchUser('ana')).toEqual([{ id: 'u1' }])

    apiMock.searchUsers.mockRejectedValueOnce(failure)
    expect(await store.searchUser('ana')).toEqual([])
    expect(store.problem.detail).toBe('boom')
  })

  it('fetchMembers() loads members into state and sets problem on failure', async () => {
    apiMock.listMembers.mockResolvedValueOnce({ data: [{ id: 'u1' }] })
    const store = useTenantStore()
    await store.fetchMembers('t1')
    expect(store.members).toEqual([{ id: 'u1' }])

    apiMock.listMembers.mockRejectedValueOnce(failure)
    await store.fetchMembers('t1')
    expect(store.problem.detail).toBe('boom')
  })

  it('addMember() returns true on success and false with problem on failure', async () => {
    apiMock.addMember.mockResolvedValueOnce({})
    const store = useTenantStore()
    expect(await store.addMember('t1', 'u1')).toBe(true)

    apiMock.addMember.mockRejectedValueOnce(failure)
    expect(await store.addMember('t1', 'u1')).toBe(false)
    expect(store.problem.detail).toBe('boom')
  })

  it('removeMember() returns true on success and false with problem on failure', async () => {
    apiMock.removeMember.mockResolvedValueOnce({})
    const store = useTenantStore()
    expect(await store.removeMember('t1', 'u1')).toBe(true)

    apiMock.removeMember.mockRejectedValueOnce(failure)
    expect(await store.removeMember('t1', 'u1')).toBe(false)
    expect(store.problem.detail).toBe('boom')
  })

  it('grantTenantAdmin() returns true on success and false with problem on failure', async () => {
    apiMock.grantTenantAdmin.mockResolvedValueOnce({})
    const store = useTenantStore()
    expect(await store.grantTenantAdmin('t1', 'u1')).toBe(true)

    apiMock.grantTenantAdmin.mockRejectedValueOnce(failure)
    expect(await store.grantTenantAdmin('t1', 'u1')).toBe(false)
    expect(store.problem.detail).toBe('boom')
  })

  it('revokeTenantAdmin() returns true on success and false with problem on failure', async () => {
    apiMock.revokeTenantAdmin.mockResolvedValueOnce({})
    const store = useTenantStore()
    expect(await store.revokeTenantAdmin('t1', 'u1')).toBe(true)

    apiMock.revokeTenantAdmin.mockRejectedValueOnce(failure)
    expect(await store.revokeTenantAdmin('t1', 'u1')).toBe(false)
    expect(store.problem.detail).toBe('boom')
  })
})
