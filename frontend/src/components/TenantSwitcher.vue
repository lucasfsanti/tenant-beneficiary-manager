<template>
  <select
    class="tenant-switcher"
    aria-label="Tenant ativo"
    :value="auth.activeTenantId"
    @change="onChange"
  >
    <option v-for="tenant in options" :key="tenant.id" :value="tenant.id">
      {{ tenant.name }}
    </option>
  </select>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth'
import tenantAdminApi from '../services/tenantAdminApi'

const auth = useAuthStore()

// A System Admin has every Normal-tier action across every tenant regardless of membership
// (FR-008) — including a System Admin with zero memberships, like the seeded `admin` account —
// so their switcher must offer every tenant, not just auth.memberships (which would be empty).
const allTenants = ref([])

const options = computed(() => (auth.isSystemAdmin ? allTenants.value : auth.memberships))

onMounted(async () => {
  if (auth.isSystemAdmin) {
    const response = await tenantAdminApi.listTenants()
    allTenants.value = response.data
    if (!auth.activeTenantId && allTenants.value.length > 0) {
      auth.setActiveTenant(allTenants.value[0].id)
    }
  }
})

function onChange(event) {
  auth.setActiveTenant(event.target.value)
}
</script>

<style scoped>
.tenant-switcher {
  padding: 0.4rem;
  border: 1px solid #ccc;
  border-radius: 4px;
}
</style>
