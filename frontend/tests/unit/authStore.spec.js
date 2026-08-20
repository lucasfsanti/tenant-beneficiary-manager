import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import axios from 'axios'
import { useAuthStore } from '../../src/stores/auth'

vi.mock('axios')

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  describe('login', () => {
    it('stores the token/user and selects the first membership as the active tenant', async () => {
      axios.post.mockResolvedValue({
        data: {
          token: 'jwt-token',
          user: { id: 'u1', username: 'ana', tenants: [{ id: 'tenant-a', name: 'Tenant Alfa' }] }
        }
      })
      const auth = useAuthStore()

      const ok = await auth.login('ana', 'demo123')

      expect(ok).toBe(true)
      expect(axios.post).toHaveBeenCalledWith('/api/auth/login', {
        username: 'ana',
        password: 'demo123'
      })
      expect(auth.token).toBe('jwt-token')
      expect(auth.activeTenantId).toBe('tenant-a')
      expect(localStorage.getItem('tbm.token')).toBe('jwt-token')
    })

    it('does not override an already-selected active tenant', async () => {
      axios.post.mockResolvedValue({
        data: {
          token: 'jwt-token',
          user: {
            id: 'u1',
            username: 'ana',
            tenants: [
              { id: 'tenant-a', name: 'Tenant Alfa' },
              { id: 'tenant-b', name: 'Tenant Beta' }
            ]
          }
        }
      })
      const auth = useAuthStore()
      auth.activeTenantId = 'tenant-b'

      await auth.login('ana', 'demo123')

      expect(auth.activeTenantId).toBe('tenant-b')
    })

    it('sets a structured error message and returns false on failure', async () => {
      axios.post.mockRejectedValue({ response: { data: { detail: 'Usuário ou senha inválidos.' } } })
      const auth = useAuthStore()

      const ok = await auth.login('ana', 'errada')

      expect(ok).toBe(false)
      expect(auth.error).toBe('Usuário ou senha inválidos.')
      expect(auth.token).toBeNull()
    })

    it('falls back to a generic error message when the failure has no detail', async () => {
      axios.post.mockRejectedValue(new Error('network down'))
      const auth = useAuthStore()

      await auth.login('ana', 'errada')

      expect(auth.error).toBe('Não foi possível entrar. Verifique suas credenciais.')
    })
  })

  describe('register', () => {
    it('resolves true on success', async () => {
      axios.post.mockResolvedValue({})
      const auth = useAuthStore()

      const ok = await auth.register('novo', 'senha123')

      expect(ok).toBe(true)
      expect(axios.post).toHaveBeenCalledWith('/api/auth/register', {
        username: 'novo',
        password: 'senha123'
      })
    })

    it('sets a structured error message and returns false on failure', async () => {
      axios.post.mockRejectedValue({ response: { data: { detail: 'Nome de usuário já cadastrado.' } } })
      const auth = useAuthStore()

      const ok = await auth.register('ja-existe', 'senha123')

      expect(ok).toBe(false)
      expect(auth.error).toBe('Nome de usuário já cadastrado.')
    })

    it('falls back to a generic error message when the failure has no detail', async () => {
      axios.post.mockRejectedValue(new Error('network down'))
      const auth = useAuthStore()

      await auth.register('novo', 'senha123')

      expect(auth.error).toBe('Não foi possível criar a conta. Tente novamente.')
    })
  })

  describe('fetchProfile', () => {
    it('does nothing when there is no token', async () => {
      const auth = useAuthStore()
      auth.token = null

      await auth.fetchProfile()

      expect(axios.get).not.toHaveBeenCalled()
    })

    it('loads the profile and selects the first membership when none is active', async () => {
      axios.get.mockResolvedValue({
        data: { id: 'u1', username: 'ana', tenants: [{ id: 'tenant-a', name: 'Tenant Alfa' }] }
      })
      const auth = useAuthStore()
      auth.token = 'jwt-token'

      await auth.fetchProfile()

      expect(axios.get).toHaveBeenCalledWith('/api/me', {
        headers: { Authorization: 'Bearer jwt-token' }
      })
      expect(auth.user.username).toBe('ana')
      expect(auth.activeTenantId).toBe('tenant-a')
    })

    it('logs out when the profile request fails (e.g. an expired token)', async () => {
      axios.get.mockRejectedValue(new Error('401'))
      const auth = useAuthStore()
      auth.token = 'stale-token'

      await auth.fetchProfile()

      expect(auth.token).toBeNull()
      expect(auth.user).toBeNull()
    })
  })

  describe('setActiveTenant / logout', () => {
    it('persists the active tenant to localStorage', () => {
      const auth = useAuthStore()

      auth.setActiveTenant('tenant-a')

      expect(auth.activeTenantId).toBe('tenant-a')
      expect(localStorage.getItem('tbm.activeTenantId')).toBe('tenant-a')
    })

    it('clears all session state and localStorage', () => {
      const auth = useAuthStore()
      auth.token = 'jwt-token'
      auth.user = { id: 'u1' }
      auth.setActiveTenant('tenant-a')

      auth.logout()

      expect(auth.token).toBeNull()
      expect(auth.user).toBeNull()
      expect(auth.activeTenantId).toBeNull()
      expect(localStorage.getItem('tbm.token')).toBeNull()
      expect(localStorage.getItem('tbm.activeTenantId')).toBeNull()
    })
  })

  describe('getters', () => {
    it('memberships is an empty array when there is no user', () => {
      const auth = useAuthStore()
      auth.user = null

      expect(auth.memberships).toEqual([])
    })

    it('memberships returns the user tenants when present', () => {
      const auth = useAuthStore()
      auth.user = { tenants: [{ id: 'tenant-a' }] }

      expect(auth.memberships).toEqual([{ id: 'tenant-a' }])
    })

    it('isTenantAdminFor reflects the membership flag for the given tenant', () => {
      const auth = useAuthStore()
      auth.user = {
        tenants: [
          { id: 'tenant-a', isTenantAdmin: true },
          { id: 'tenant-b', isTenantAdmin: false }
        ]
      }

      expect(auth.isTenantAdminFor('tenant-a')).toBe(true)
      expect(auth.isTenantAdminFor('tenant-b')).toBe(false)
      expect(auth.isTenantAdminFor('tenant-unknown')).toBe(false)
    })
  })
})
