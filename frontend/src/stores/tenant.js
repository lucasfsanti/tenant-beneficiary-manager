import { defineStore } from 'pinia'
import tenantAdminApi from '../services/tenantAdminApi'
import { extractProblem } from '../services/problemDetail'

export const useTenantStore = defineStore('tenant', {
  state: () => ({
    items: [],
    members: [],
    loading: false,
    problem: null
  }),
  actions: {
    async fetchList() {
      this.loading = true
      this.problem = null
      try {
        const response = await tenantAdminApi.listTenants()
        this.items = response.data
      } catch (err) {
        this.problem = extractProblem(err)
      } finally {
        this.loading = false
      }
    },
    async create(payload) {
      this.problem = null
      try {
        await tenantAdminApi.createTenant(payload)
        return true
      } catch (err) {
        this.problem = extractProblem(err)
        return false
      }
    },
    async update(id, payload) {
      this.problem = null
      try {
        await tenantAdminApi.updateTenant(id, payload)
        return true
      } catch (err) {
        this.problem = extractProblem(err)
        return false
      }
    },
    async remove(id) {
      this.problem = null
      try {
        await tenantAdminApi.removeTenant(id)
        return true
      } catch (err) {
        this.problem = extractProblem(err)
        return false
      }
    },
    async grantSystemAdmin(userId) {
      this.problem = null
      try {
        await tenantAdminApi.grantSystemAdmin(userId)
        return true
      } catch (err) {
        this.problem = extractProblem(err)
        return false
      }
    },
    async revokeSystemAdmin(userId) {
      this.problem = null
      try {
        await tenantAdminApi.revokeSystemAdmin(userId)
        return true
      } catch (err) {
        this.problem = extractProblem(err)
        return false
      }
    },
    async searchUser(username) {
      this.problem = null
      try {
        const response = await tenantAdminApi.searchUsers(username)
        return response.data
      } catch (err) {
        this.problem = extractProblem(err)
        return []
      }
    },
    async fetchMembers(tenantId) {
      this.problem = null
      try {
        const response = await tenantAdminApi.listMembers(tenantId)
        this.members = response.data
      } catch (err) {
        this.problem = extractProblem(err)
      }
    },
    async addMember(tenantId, userId) {
      this.problem = null
      try {
        await tenantAdminApi.addMember(tenantId, userId)
        return true
      } catch (err) {
        this.problem = extractProblem(err)
        return false
      }
    },
    async removeMember(tenantId, userId) {
      this.problem = null
      try {
        await tenantAdminApi.removeMember(tenantId, userId)
        return true
      } catch (err) {
        this.problem = extractProblem(err)
        return false
      }
    },
    async grantTenantAdmin(tenantId, userId) {
      this.problem = null
      try {
        await tenantAdminApi.grantTenantAdmin(tenantId, userId)
        return true
      } catch (err) {
        this.problem = extractProblem(err)
        return false
      }
    },
    async revokeTenantAdmin(tenantId, userId) {
      this.problem = null
      try {
        await tenantAdminApi.revokeTenantAdmin(tenantId, userId)
        return true
      } catch (err) {
        this.problem = extractProblem(err)
        return false
      }
    }
  }
})
