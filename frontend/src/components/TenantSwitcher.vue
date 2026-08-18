<template>
  <select
    class="tenant-switcher"
    aria-label="Tenant ativo"
    :value="auth.activeTenantId"
    @change="onChange"
  >
    <option v-for="tenant in auth.memberships" :key="tenant.id" :value="tenant.id">
      {{ tenant.name }}
    </option>
  </select>
</template>

<script setup>
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()

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
