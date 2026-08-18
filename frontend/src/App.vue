<template>
  <div class="app-shell">
    <header v-if="auth.isAuthenticated" class="app-header">
      <nav class="app-nav">
        <router-link to="/pessoas">Pessoas</router-link>
        <router-link to="/beneficiarios">Beneficiários</router-link>
        <router-link v-if="auth.isSystemAdmin" to="/tenants">Tenants</router-link>
        <router-link v-if="auth.isSystemAdmin" to="/admins">Administradores</router-link>
        <router-link
          v-else-if="auth.activeTenantId && auth.isTenantAdminFor(auth.activeTenantId)"
          :to="`/tenants/${auth.activeTenantId}/editar`"
        >
          Meu Tenant
        </router-link>
      </nav>
      <div class="app-header__tenant">
        <TenantSwitcher />
        <ActiveTenantBadge />
        <button class="button button--secondary" @click="handleLogout">Sair</button>
      </div>
    </header>
    <router-view />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import TenantSwitcher from './components/TenantSwitcher.vue'
import ActiveTenantBadge from './components/ActiveTenantBadge.vue'

const auth = useAuthStore()
const router = useRouter()

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>
