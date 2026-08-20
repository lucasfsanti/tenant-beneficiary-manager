import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ActiveTenantBadge from '../../src/components/ActiveTenantBadge.vue'
import { useAuthStore } from '../../src/stores/auth'

describe('ActiveTenantBadge', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('shows the active tenant name', () => {
    const auth = useAuthStore()
    auth.user = { id: 'user-1', tenants: [{ id: 'tenant-a', name: 'Tenant Alfa' }] }
    auth.activeTenantId = 'tenant-a'

    const wrapper = mount(ActiveTenantBadge)

    expect(wrapper.text()).toContain('Tenant Alfa')
  })

  it('shows a placeholder when there is no active tenant', () => {
    const auth = useAuthStore()
    auth.user = { id: 'user-1', tenants: [] }
    auth.activeTenantId = null

    const wrapper = mount(ActiveTenantBadge)

    expect(wrapper.text()).toContain('—')
  })
})
