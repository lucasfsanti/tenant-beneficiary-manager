import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import router from '../../src/router'
import { useAuthStore } from '../../src/stores/auth'

describe('router navigation guards', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    router.push('/')
    await router.isReady()
  })

  it('redirects to login with a redirect query when the user is not authenticated', async () => {
    await router.push('/pessoas')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/pessoas')
  })

  it('allows navigating to a public route while unauthenticated', async () => {
    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('redirects away from /login to /pessoas once already authenticated', async () => {
    const auth = useAuthStore()
    auth.token = 'jwt-token'

    await router.push('/login')

    expect(router.currentRoute.value.path).toBe('/pessoas')
  })

  it('redirects a non-system-admin away from a system-admin-only route', async () => {
    const auth = useAuthStore()
    auth.token = 'jwt-token'
    auth.user = { id: 'u1', tenants: [] }

    await router.push('/tenants')

    expect(router.currentRoute.value.path).toBe('/pessoas')
  })

  it('allows a system admin into a system-admin-only route', async () => {
    const auth = useAuthStore()
    auth.token = 'jwt-token'
    auth.user = { id: 'u1', isSystemAdmin: true, tenants: [] }

    await router.push('/tenants')

    expect(router.currentRoute.value.name).toBe('tenants-list')
  })

  it('redirects a user who is neither system admin nor tenant admin of the target tenant', async () => {
    const auth = useAuthStore()
    auth.token = 'jwt-token'
    auth.user = { id: 'u1', tenants: [{ id: 'tenant-a', isTenantAdmin: false }] }

    await router.push('/tenants/tenant-a/editar')

    expect(router.currentRoute.value.path).toBe('/pessoas')
  })

  it('allows the tenant admin of the target tenant into its edit route', async () => {
    const auth = useAuthStore()
    auth.token = 'jwt-token'
    auth.user = { id: 'u1', tenants: [{ id: 'tenant-a', isTenantAdmin: true }] }

    await router.push('/tenants/tenant-a/editar')

    expect(router.currentRoute.value.name).toBe('tenants-edit')
  })
})
