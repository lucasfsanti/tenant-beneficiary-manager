import api from './api'

export default {
  listTenants() {
    return api.get('/tenants')
  },
  getTenant(id) {
    return api.get(`/tenants/${id}`)
  },
  createTenant(payload) {
    return api.post('/tenants', payload)
  },
  updateTenant(id, payload) {
    return api.put(`/tenants/${id}`, payload)
  },
  removeTenant(id) {
    return api.delete(`/tenants/${id}`)
  },
  grantSystemAdmin(userId) {
    return api.put(`/users/${userId}/system-admin`)
  },
  revokeSystemAdmin(userId) {
    return api.delete(`/users/${userId}/system-admin`)
  },
  searchUsers(username) {
    return api.get('/users', { params: { username } })
  },
  listMembers(tenantId) {
    return api.get(`/tenants/${tenantId}/members`)
  },
  addMember(tenantId, userId) {
    return api.post(`/tenants/${tenantId}/members`, { userId })
  },
  removeMember(tenantId, userId) {
    return api.delete(`/tenants/${tenantId}/members/${userId}`)
  },
  grantTenantAdmin(tenantId, userId) {
    return api.put(`/tenants/${tenantId}/members/${userId}/tenant-admin`)
  },
  revokeTenantAdmin(tenantId, userId) {
    return api.delete(`/tenants/${tenantId}/members/${userId}/tenant-admin`)
  }
}
