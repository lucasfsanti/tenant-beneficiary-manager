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
        <input v-model="newMemberUsername" type="text" placeholder="Nome de usuário" required />
        <button type="submit" class="button">Adicionar</button>
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

const props = defineProps({
  id: { type: String, default: null }
})

const isEdit = computed(() => !!props.id)
const store = useTenantStore()
const auth = useAuthStore()
const router = useRouter()
const saving = ref(false)
const newMemberUsername = ref('')

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
  const results = await store.searchUser(newMemberUsername.value)
  if (results.length === 0) {
    store.problem = {
      title: 'Usuário não encontrado',
      detail: `Nenhum usuário com o nome de usuário "${newMemberUsername.value}".`
    }
    return
  }
  const ok = await store.addMember(props.id, results[0].id)
  if (ok) {
    newMemberUsername.value = ''
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

.add-member-form input {
  flex: 1;
  max-width: 320px;
}

.actions {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}
</style>
