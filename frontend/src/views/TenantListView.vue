<template>
  <div>
    <div class="view-header">
      <h1>Tenants</h1>
      <router-link class="button" to="/tenants/novo">Novo Tenant</router-link>
    </div>

    <ErrorBanner v-if="store.problem" :problem="store.problem" />

    <p v-if="store.loading">Carregando...</p>
    <p v-else-if="store.items.length === 0" class="empty-state">Nenhum tenant encontrado.</p>
    <table v-else>
      <thead>
        <tr>
          <th>Nome</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="tenant in store.items" :key="tenant.id">
          <td>{{ tenant.name }}</td>
          <td class="actions">
            <router-link :to="`/tenants/${tenant.id}/editar`">Editar</router-link>
            <button class="button button--danger" @click="handleDelete(tenant)">Excluir</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useTenantStore } from '../stores/tenant'
import ErrorBanner from '../components/ErrorBanner.vue'

const store = useTenantStore()

function load() {
  store.fetchList()
}

async function handleDelete(tenant) {
  if (!window.confirm(`Excluir o tenant "${tenant.name}"?`)) {
    return
  }
  const ok = await store.remove(tenant.id)
  if (ok) {
    load()
  }
}

onMounted(load)
</script>

<style scoped>
.view-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}
</style>
