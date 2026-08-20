<template>
  <div class="form-view">
    <h1>{{ isEdit ? 'Editar Tenant' : 'Novo Tenant' }}</h1>

    <ErrorBanner v-if="store.problem" :problem="store.problem" />

    <form @submit.prevent="handleSubmit">
      <label for="name">Nome</label>
      <input id="name" v-model="form.name" type="text" required />

      <div class="form-actions">
        <button type="submit" class="button" :disabled="saving">
          {{ saving ? 'Salvando...' : 'Salvar' }}
        </button>
        <router-link class="button button--secondary" :to="cancelTo">Cancelar</router-link>
      </div>
    </form>

    <section v-if="isEdit" class="members-section">
      <h2>Membros</h2>

      <form class="add-member-form" @submit.prevent="handleAddMember">
        <SearchableSelect
          :key="memberSearchKey"
          v-model="selectedUserId"
          :search="searchUsers"
          :option-label="userOptionLabel"
          placeholder="Nome de usuário"
        />
        <button type="submit" class="button" :disabled="!selectedUserId">Adicionar</button>
      </form>

      <p v-if="store.members.length === 0" class="empty-state">Nenhum membro neste tenant.</p>
      <table v-else>
        <thead>
          <tr>
            <th>Usuário</th>
            <th>Tenant Admin</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="member in store.members" :key="member.userId">
            <td>{{ member.username }}</td>
            <td>{{ member.isTenantAdmin ? 'Sim' : 'Não' }}</td>
            <td class="actions">
              <button
                v-if="!member.isTenantAdmin"
                class="button"
                @click="handleGrantTenantAdmin(member)"
              >
                Conceder Tenant Admin
              </button>
              <button v-else class="button button--secondary" @click="handleRevokeTenantAdmin(member)">
                Revogar Tenant Admin
              </button>
              <button class="button button--danger" @click="handleRemoveMember(member)">Remover</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useTenantStore } from '../stores/tenant'
import { useAuthStore } from '../stores/auth'
import tenantAdminApi from '../services/tenantAdminApi'
import ErrorBanner from '../components/ErrorBanner.vue'
import SearchableSelect from '../components/SearchableSelect.vue'

const props = defineProps({
  id: { type: String, default: null }
})

const isEdit = computed(() => !!props.id)
const store = useTenantStore()
const auth = useAuthStore()
const router = useRouter()
const saving = ref(false)
const selectedUserId = ref(null)
const memberSearchKey = ref(0)

// Below the backend's own 2-character minimum (research.md §3), skip the network round-trip
// entirely — the backend would just return [] anyway, so this is a network-efficiency nicety
// layered on top of, not instead of, the server-side enforcement.
function searchUsers(username) {
  if (username.trim().length < 2) {
    return []
  }
  return store.searchUser(username)
}

function userOptionLabel(user) {
  return user.username
}

const cancelTo = computed(() => (auth.isSystemAdmin ? '/tenants' : '/pessoas'))

const form = reactive({
  name: ''
})

onMounted(async () => {
  if (isEdit.value) {
    const response = await tenantAdminApi.getTenant(props.id)
    form.name = response.data.name
    await store.fetchMembers(props.id)
  }
})

async function handleSubmit() {
  saving.value = true
  const payload = { name: form.name }
  const ok = isEdit.value
    ? await store.update(props.id, payload)
    : await store.create(payload)
  saving.value = false
  if (ok) {
    router.push(cancelTo.value)
  }
}

async function handleAddMember() {
  if (!selectedUserId.value) {
    return
  }
  const ok = await store.addMember(props.id, selectedUserId.value)
  if (ok) {
    selectedUserId.value = null
    memberSearchKey.value += 1
    await store.fetchMembers(props.id)
  }
}

async function handleRemoveMember(member) {
  if (!window.confirm(`Remover "${member.username}" deste tenant?`)) {
    return
  }
  const ok = await store.removeMember(props.id, member.userId)
  if (ok) {
    await store.fetchMembers(props.id)
  }
}

async function handleGrantTenantAdmin(member) {
  const ok = await store.grantTenantAdmin(props.id, member.userId)
  if (ok) {
    await store.fetchMembers(props.id)
  }
}

async function handleRevokeTenantAdmin(member) {
  const ok = await store.revokeTenantAdmin(props.id, member.userId)
  if (ok) {
    await store.fetchMembers(props.id)
  }
}
</script>

<style scoped>
.form-view {
  max-width: 640px;
}

form {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

label {
  font-weight: 600;
}

input {
  padding: 0.5rem;
  border: 1px solid #ccc;
  border-radius: 4px;
}

.form-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: 1rem;
}

.members-section {
  margin-top: 2rem;
}

.add-member-form {
  flex-direction: row;
  margin-bottom: 1rem;
}

.add-member-form .searchable-select {
  flex: 1;
  max-width: 320px;
}

.actions {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
</style>
