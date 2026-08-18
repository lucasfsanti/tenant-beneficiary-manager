<template>
  <div>
    <div class="view-header">
      <h1>Pessoas</h1>
      <router-link class="button" to="/pessoas/novo">Nova Pessoa</router-link>
    </div>

    <input
      v-model="nomeFilter"
      class="filter-input"
      type="search"
      placeholder="Filtrar por nome..."
      @input="onFilterChange"
    />

    <ErrorBanner v-if="store.problem" :problem="store.problem" />

    <p v-if="store.loading">Carregando...</p>
    <p v-else-if="store.items.length === 0" class="empty-state">Nenhuma pessoa encontrada.</p>
    <table v-else>
      <thead>
        <tr>
          <th>Nome</th>
          <th>CPF</th>
          <th>E-mail</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="pessoa in store.items" :key="pessoa.id">
          <td>{{ pessoa.nome }}</td>
          <td>{{ pessoa.cpf }}</td>
          <td>{{ pessoa.email || '—' }}</td>
          <td class="actions">
            <router-link :to="`/pessoas/${pessoa.id}/editar`">Editar</router-link>
            <button class="button button--danger" @click="handleDelete(pessoa)">Excluir</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { usePessoaStore } from '../stores/pessoa'
import ErrorBanner from '../components/ErrorBanner.vue'

const store = usePessoaStore()
const nomeFilter = ref('')
let debounceHandle = null

function load() {
  store.fetchList({ nome: nomeFilter.value })
}

function onFilterChange() {
  clearTimeout(debounceHandle)
  debounceHandle = setTimeout(load, 300)
}

async function handleDelete(pessoa) {
  if (!window.confirm(`Excluir "${pessoa.nome}"?`)) {
    return
  }
  const ok = await store.remove(pessoa.id)
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

.filter-input {
  padding: 0.5rem;
  margin-bottom: 1rem;
  width: 100%;
  max-width: 320px;
  border: 1px solid #ccc;
  border-radius: 4px;
}

.actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}
</style>
