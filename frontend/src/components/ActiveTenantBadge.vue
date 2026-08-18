<template>
  <span class="active-tenant-badge">
    Tenant ativo: <strong>{{ auth.activeTenant?.name || '—' }}</strong>
  </span>
</template>

<script setup>
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
</script>

<style scoped>
.active-tenant-badge {
  font-size: 0.9rem;
  color: #4a5568;
}
</style>
