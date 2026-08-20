<template>
  <div class="form-view">
    <h1>{{ isEdit ? 'Editar Beneficiário' : 'Novo Beneficiário' }}</h1>

    <ErrorBanner v-if="store.problem" :problem="store.problem" />

    <form @submit.prevent="handleSubmit">
      <label for="pessoaId">Pessoa</label>
      <SearchableSelect
        id="pessoaId"
        v-model="form.pessoaId"
        :search="searchPessoas"
        :option-label="pessoaOptionLabel"
        :initial-label="pessoaInitialLabel"
        placeholder="Digite para buscar uma pessoa..."
      />

      <label for="matricula">Matrícula</label>
      <input id="matricula" v-model="form.matricula" type="text" required />

      <label for="tipo">Tipo</label>
      <select id="tipo" v-model="form.tipo" required>
        <option value="TITULAR">Titular</option>
        <option value="DEPENDENTE">Dependente</option>
      </select>

      <label for="status">Status</label>
      <select id="status" v-model="form.status" required>
        <option value="ATIVO">Ativo</option>
        <option value="INATIVO">Inativo</option>
      </select>

      <label for="dataAdesao">Data de adesão (opcional)</label>
      <input id="dataAdesao" v-model="form.dataAdesao" type="date" />

      <div class="form-actions">
        <button type="submit" class="button" :disabled="saving">
          {{ saving ? 'Salvando...' : 'Salvar' }}
        </button>
        <router-link class="button button--secondary" to="/beneficiarios">Cancelar</router-link>
      </div>
    </form>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useBeneficiarioStore } from '../stores/beneficiario'
import beneficiarioApi from '../services/beneficiarioApi'
import pessoaApi from '../services/pessoaApi'
import ErrorBanner from '../components/ErrorBanner.vue'
import SearchableSelect from '../components/SearchableSelect.vue'

const props = defineProps({
  id: { type: String, default: null }
})

const isEdit = computed(() => !!props.id)
const store = useBeneficiarioStore()
const router = useRouter()
const saving = ref(false)
const pessoaInitialLabel = ref('')

const form = reactive({
  pessoaId: '',
  matricula: '',
  tipo: 'TITULAR',
  status: 'ATIVO',
  dataAdesao: ''
})

async function searchPessoas(nome) {
  const response = await pessoaApi.list({ nome, size: 20 })
  return response.data.content
}

function pessoaOptionLabel(pessoa) {
  return `${pessoa.nome} (${pessoa.cpf})`
}

onMounted(async () => {
  if (isEdit.value) {
    const response = await beneficiarioApi.get(props.id)
    form.pessoaId = response.data.pessoaId
    form.matricula = response.data.matricula
    form.tipo = response.data.tipo
    form.status = response.data.status
    form.dataAdesao = response.data.dataAdesao || ''
    pessoaInitialLabel.value = response.data.pessoaNome
  }
})

async function handleSubmit() {
  if (!form.pessoaId) {
    return
  }
  saving.value = true
  const payload = {
    pessoaId: form.pessoaId,
    matricula: form.matricula,
    tipo: form.tipo,
    status: form.status,
    dataAdesao: form.dataAdesao || null
  }
  const ok = isEdit.value
    ? await store.update(props.id, payload)
    : await store.create(payload)
  saving.value = false
  if (ok) {
    router.push('/beneficiarios')
  }
}
</script>

<style scoped>
.form-view {
  max-width: 480px;
}

form {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

label {
  font-weight: 600;
}

input,
select {
  padding: 0.5rem;
  border: 1px solid #ccc;
  border-radius: 4px;
}

.form-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: 1rem;
}
</style>
