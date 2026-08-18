import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import TenantSwitcher from '../../src/components/TenantSwitcher.vue'
import { useAuthStore } from '../../src/stores/auth'

describe('TenantSwitcher', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it("renders only the signed-in user's memberships", () => {
    const auth = useAuthStore()
    auth.user = {
      id: 'user-1',
      username: 'ana',
      tenants: [
        { id: 'tenant-a', name: 'Tenant Alfa' },
        { id: 'tenant-b', name: 'Tenant Beta' }
      ]
    }
    auth.activeTenantId = 'tenant-a'

    const wrapper = mount(TenantSwitcher)
    const options = wrapper.findAll('option')

    expect(options).toHaveLength(2)
    expect(options.map((o) => o.text())).toEqual(['Tenant Alfa', 'Tenant Beta'])
  })

  it('does not render tenants the user is not a member of', () => {
    const auth = useAuthStore()
    auth.user = {
      id: 'user-2',
      username: 'bruno',
      tenants: [{ id: 'tenant-a', name: 'Tenant Alfa' }]
    }
    auth.activeTenantId = 'tenant-a'

    const wrapper = mount(TenantSwitcher)
    const options = wrapper.findAll('option')

    expect(options).toHaveLength(1)
    expect(options[0].text()).toBe('Tenant Alfa')
  })
})
