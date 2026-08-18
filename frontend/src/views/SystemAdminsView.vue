<template>
  <div>
    <h1>Administradores do Sistema</h1>
    <p class="hint">
      Busque um usuário pelo nome de usuário exato para conceder ou revogar o status de System
      Admin.
    </p>

    <form class="search-form" @submit.prevent="handleSearch">
      <input v-model="username" type="text" placeholder="Nome de usuário" required />
      <button type="submit" class="button" :disabled="searching">
        {{ searching ? 'Buscando...' : 'Buscar' }}
      </button>
    </form>

    <ErrorBanner v-if="store.problem" :problem="store.problem" />
    <p v-if="searched && !searching && results.length === 0" class="empty-state">
      Nenhum usuário encontrado com esse nome de usuário.
    </p>

    <table v-if="results.length > 0">
      <thead>
        <tr>
          <th>Usuário</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in results" :key="user.id">
          <td>{{ user.username }}</td>
          <td class="actions">
            <button class="button" @click="handleGrant(user)">Conceder System Admin</button>
            <button class="button button--danger" @click="handleRevoke(user)">
              Revogar System Admin
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-if="statusMessage" class="status-message">{{ statusMessage }}</p>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useTenantStore } from '../stores/tenant'
import ErrorBanner from '../components/ErrorBanner.vue'

const store = useTenantStore()
const username = ref('')
const results = ref([])
const searching = ref(false)
const searched = ref(false)
const statusMessage = ref('')

async function handleSearch() {
  searching.value = true
  searched.value = true
  statusMessage.value = ''
  results.value = await store.searchUser(username.value)
  searching.value = false
}

async function handleGrant(user) {
  statusMessage.value = ''
  const ok = await store.grantSystemAdmin(user.id)
  if (ok) {
    statusMessage.value = `"${user.username}" agora é System Admin.`
  }
}

async function handleRevoke(user) {
  statusMessage.value = ''
  const ok = await store.revokeSystemAdmin(user.id)
  if (ok) {
    statusMessage.value = `Status de System Admin de "${user.username}" foi revogado.`
  }
}
</script>

<style scoped>
.hint {
  color: #555;
  margin-bottom: 1rem;
}

.search-form {
  display: flex;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.search-form input {
  padding: 0.5rem;
  border: 1px solid #ccc;
  border-radius: 4px;
  flex: 1;
  max-width: 320px;
}

.actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.status-message {
  margin-top: 1rem;
  font-weight: 600;
}
</style>
