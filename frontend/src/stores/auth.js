import { defineStore } from 'pinia'
import axios from 'axios'

const TOKEN_KEY = 'tbm.token'
const TENANT_KEY = 'tbm.activeTenantId'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || null,
    user: null,
    activeTenantId: localStorage.getItem(TENANT_KEY) || null,
    error: null
  }),
  getters: {
    isAuthenticated: (state) => !!state.token,
    memberships: (state) => state.user?.tenants || [],
    activeTenant: (state) =>
      state.user?.tenants?.find((t) => t.id === state.activeTenantId) || null,
    isSystemAdmin: (state) => !!state.user?.isSystemAdmin,
    isTenantAdminFor: (state) => (tenantId) =>
      !!state.user?.tenants?.find((t) => t.id === tenantId)?.isTenantAdmin
  },
  actions: {
    async login(username, password) {
      this.error = null
      try {
        const response = await axios.post('/api/auth/login', { username, password })
        this.token = response.data.token
        this.user = response.data.user
        localStorage.setItem(TOKEN_KEY, this.token)
        if (!this.activeTenantId && this.user.tenants.length > 0) {
          this.setActiveTenant(this.user.tenants[0].id)
        }
        return true
      } catch (err) {
        this.error =
          err.response?.data?.detail || 'Não foi possível entrar. Verifique suas credenciais.'
        return false
      }
    },
    async fetchProfile() {
      if (!this.token) return
      try {
        const response = await axios.get('/api/me', {
          headers: { Authorization: `Bearer ${this.token}` }
        })
        this.user = response.data
        if (!this.activeTenantId && this.user.tenants.length > 0) {
          this.setActiveTenant(this.user.tenants[0].id)
        }
      } catch {
        this.logout()
      }
    },
    setActiveTenant(tenantId) {
      this.activeTenantId = tenantId
      localStorage.setItem(TENANT_KEY, tenantId)
    },
    logout() {
      this.token = null
      this.user = null
      this.activeTenantId = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(TENANT_KEY)
    }
  }
})
