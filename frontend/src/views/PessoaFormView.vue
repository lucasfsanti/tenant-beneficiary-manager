<template>
  <div class="form-view">
    <h1>{{ isEdit ? 'Editar Pessoa' : 'Nova Pessoa' }}</h1>

    <ErrorBanner v-if="store.problem" :problem="store.problem" />

    <form @submit.prevent="handleSubmit">
      <label for="nome">Nome</label>
      <input id="nome" v-model="form.nome" type="text" required />

      <label for="cpf">CPF</label>
      <input id="cpf" v-model="form.cpf" type="text" maxlength="11" required />

      <label for="dataNascimento">Data de nascimento (opcional)</label>
      <input id="dataNascimento" v-model="form.dataNascimento" type="date" />

      <label for="email">E-mail (opcional)</label>
      <input id="email" v-model="form.email" type="email" />

      <div class="form-actions">
        <button type="submit" class="button" :disabled="saving">
          {{ saving ? 'Salvando...' : 'Salvar' }}
        </button>
        <router-link class="button button--secondary" to="/pessoas">Cancelar</router-link>
      </div>
    </form>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { usePessoaStore } from '../stores/pessoa'
import pessoaApi from '../services/pessoaApi'
import ErrorBanner from '../components/ErrorBanner.vue'

const props = defineProps({
  id: { type: String, default: null }
})

const isEdit = computed(() => !!props.id)
const store = usePessoaStore()
const router = useRouter()
const saving = ref(false)

const form = reactive({
  nome: '',
  cpf: '',
  dataNascimento: '',
  email: ''
})

onMounted(async () => {
  if (isEdit.value) {
    const response = await pessoaApi.get(props.id)
    form.nome = response.data.nome
    form.cpf = response.data.cpf
    form.dataNascimento = response.data.dataNascimento || ''
    form.email = response.data.email || ''
  }
})

async function handleSubmit() {
  saving.value = true
  const payload = {
    nome: form.nome,
    cpf: form.cpf,
    dataNascimento: form.dataNascimento || null,
    email: form.email || null
  }
  const ok = isEdit.value
    ? await store.update(props.id, payload)
    : await store.create(payload)
  saving.value = false
  if (ok) {
    router.push('/pessoas')
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
</style>
