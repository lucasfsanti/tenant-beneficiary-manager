import { describe, it, expect, vi } from 'vitest'
import tenantAdminApi from '../../src/services/tenantAdminApi'

const { apiMock } = vi.hoisted(() => ({
  apiMock: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }
}))
vi.mock('../../src/services/api', () => ({ default: apiMock }))

describe('tenantAdminApi', () => {
  it('listTenants() GETs /tenants', () => {
    tenantAdminApi.listTenants()
    expect(apiMock.get).toHaveBeenCalledWith('/tenants')
  })

  it('getTenant() GETs /tenants/:id', () => {
    tenantAdminApi.getTenant('t1')
    expect(apiMock.get).toHaveBeenCalledWith('/tenants/t1')
  })

  it('createTenant() POSTs to /tenants with the payload', () => {
    const payload = { name: 'Tenant Novo' }
    tenantAdminApi.createTenant(payload)
    expect(apiMock.post).toHaveBeenCalledWith('/tenants', payload)
  })

  it('updateTenant() PUTs to /tenants/:id with the payload', () => {
    const payload = { name: 'Renomeado' }
    tenantAdminApi.updateTenant('t1', payload)
    expect(apiMock.put).toHaveBeenCalledWith('/tenants/t1', payload)
  })

  it('removeTenant() DELETEs /tenants/:id', () => {
    tenantAdminApi.removeTenant('t1')
    expect(apiMock.delete).toHaveBeenCalledWith('/tenants/t1')
  })

  it('grantSystemAdmin() PUTs to /users/:id/system-admin', () => {
    tenantAdminApi.grantSystemAdmin('u1')
    expect(apiMock.put).toHaveBeenCalledWith('/users/u1/system-admin')
  })

  it('revokeSystemAdmin() DELETEs /users/:id/system-admin', () => {
    tenantAdminApi.revokeSystemAdmin('u1')
    expect(apiMock.delete).toHaveBeenCalledWith('/users/u1/system-admin')
  })

  it('searchUsers() GETs /users with a username param', () => {
    tenantAdminApi.searchUsers('ana')
    expect(apiMock.get).toHaveBeenCalledWith('/users', { params: { username: 'ana' } })
  })

  it('listMembers() GETs /tenants/:id/members', () => {
    tenantAdminApi.listMembers('t1')
    expect(apiMock.get).toHaveBeenCalledWith('/tenants/t1/members')
  })

  it('addMember() POSTs to /tenants/:id/members with the userId', () => {
    tenantAdminApi.addMember('t1', 'u1')
    expect(apiMock.post).toHaveBeenCalledWith('/tenants/t1/members', { userId: 'u1' })
  })

  it('removeMember() DELETEs /tenants/:id/members/:userId', () => {
    tenantAdminApi.removeMember('t1', 'u1')
    expect(apiMock.delete).toHaveBeenCalledWith('/tenants/t1/members/u1')
  })

  it('grantTenantAdmin() PUTs to /tenants/:id/members/:userId/tenant-admin', () => {
    tenantAdminApi.grantTenantAdmin('t1', 'u1')
    expect(apiMock.put).toHaveBeenCalledWith('/tenants/t1/members/u1/tenant-admin')
  })

  it('revokeTenantAdmin() DELETEs /tenants/:id/members/:userId/tenant-admin', () => {
    tenantAdminApi.revokeTenantAdmin('t1', 'u1')
    expect(apiMock.delete).toHaveBeenCalledWith('/tenants/t1/members/u1/tenant-admin')
  })
})
