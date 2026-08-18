import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import router from '../../src/router'
import { useAuthStore } from '../../src/stores/auth'

// TenantListView itself carries no role check — the /tenants route is gated by the
// `requiresSystemAdmin` router guard (router/index.js), so that's what's exercised here.
describe('Tenant management route guard (TenantListView reachability)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('redirects away from /tenants when the signed-in user is not a System Admin', async () => {
    const auth = useAuthStore()
    auth.token = 'fake-token'
    auth.user = { id: 'user-1', username: 'ana', isSystemAdmin: false, tenants: [] }

    await router.push('/tenants')

    expect(router.currentRoute.value.path).not.toBe('/tenants')
  })

  it('allows /tenants when the signed-in user is a System Admin', async () => {
    const auth = useAuthStore()
    auth.token = 'fake-token'
    auth.user = { id: 'user-2', username: 'admin', isSystemAdmin: true, tenants: [] }

    await router.push('/tenants')

    expect(router.currentRoute.value.path).toBe('/tenants')
  })

  it('also redirects away from /admins when not a System Admin', async () => {
    const auth = useAuthStore()
    auth.token = 'fake-token'
    auth.user = { id: 'user-3', username: 'bruno', isSystemAdmin: false, tenants: [] }

    await router.push('/admins')

    expect(router.currentRoute.value.path).not.toBe('/admins')
  })
})
