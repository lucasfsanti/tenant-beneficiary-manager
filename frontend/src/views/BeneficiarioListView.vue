<template>
  <div>
    <div class="view-header">
      <h1>Beneficiários</h1>
      <router-link class="button" to="/beneficiarios/novo">Novo Beneficiário</router-link>
    </div>

    <div class="filters">
      <input
        v-model="pessoaNomeFilter"
        class="filter-input"
        type="search"
        placeholder="Filtrar por nome da pessoa..."
        @input="onFilterChange"
      />
      <select v-model="statusFilter" @change="onFilterChange">
        <option value="">Todos os status</option>
        <option value="ATIVO">Ativo</option>
        <option value="INATIVO">Inativo</option>
      </select>
    </div>

    <ErrorBanner v-if="store.problem" :problem="store.problem" />

    <p v-if="store.loading">Carregando...</p>
    <p v-else-if="store.items.length === 0" class="empty-state">
      Nenhum beneficiário encontrado para os filtros aplicados.
    </p>
    <template v-else>
      <table>
        <thead>
          <tr>
            <th>Pessoa</th>
            <th>Matrícula</th>
            <th>Tipo</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="beneficiario in store.items" :key="beneficiario.id">
            <td>{{ beneficiario.pessoaNome }}</td>
            <td>{{ beneficiario.matricula }}</td>
            <td>{{ beneficiario.tipo === 'TITULAR' ? 'Titular' : 'Dependente' }}</td>
            <td>{{ beneficiario.status === 'ATIVO' ? 'Ativo' : 'Inativo' }}</td>
            <td class="actions">
              <router-link :to="`/beneficiarios/${beneficiario.id}/editar`">Editar</router-link>
              <button class="button button--danger" @click="handleDelete(beneficiario)">
                Excluir
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <PaginationControl :page="store.page" :total-pages="store.totalPages" @change="goToPage" />
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useBeneficiarioStore } from '../stores/beneficiario'
import { useAuthStore } from '../stores/auth'
import ErrorBanner from '../components/ErrorBanner.vue'
import PaginationControl from '../components/PaginationControl.vue'

const store = useBeneficiarioStore()
const auth = useAuthStore()
const pessoaNomeFilter = ref('')
const statusFilter = ref('')
let debounceHandle = null

function load(page = 0) {
  store.fetchList({
    pessoaNome: pessoaNomeFilter.value,
    status: statusFilter.value || undefined,
    page
  })
}

function onFilterChange() {
  clearTimeout(debounceHandle)
  debounceHandle = setTimeout(() => load(0), 300)
}

function goToPage(page) {
  load(page)
}

async function handleDelete(beneficiario) {
  if (!window.confirm(`Excluir o beneficiário "${beneficiario.matricula}"?`)) {
    return
  }
  const ok = await store.remove(beneficiario.id)
  if (ok) {
    load(store.page)
  }
}

watch(
  () => auth.activeTenantId,
  () => {
    store.clear()
    load(0)
  }
)

onMounted(() => load(0))
</script>

<style scoped>
.view-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.filters {
  display: flex;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.filter-input {
  padding: 0.5rem;
  width: 100%;
  max-width: 320px;
  border: 1px solid #ccc;
  border-radius: 4px;
}

select {
  padding: 0.5rem;
  border: 1px solid #ccc;
  border-radius: 4px;
}

.actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}
</style>
