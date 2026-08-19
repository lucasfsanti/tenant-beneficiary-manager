<template>
  <div class="create-user-view">
    <h1>Criar conta</h1>
    <ErrorBanner v-if="auth.error" :problem="{ title: 'Falha ao criar conta', detail: auth.error }" />
    <form @submit.prevent="handleSubmit">
      <label for="username">Usuário</label>
      <input id="username" v-model="username" type="text" autocomplete="username" required />

      <label for="password">Senha</label>
      <input
        id="password"
        v-model="password"
        type="password"
        autocomplete="new-password"
        required
      />

      <button type="submit" :disabled="loading">
        {{ loading ? 'Criando...' : 'Criar conta' }}
      </button>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import ErrorBanner from '../components/ErrorBanner.vue'

const username = ref('')
const password = ref('')
const loading = ref(false)
const auth = useAuthStore()
const router = useRouter()

async function handleSubmit() {
  loading.value = true
  const success = await auth.register(username.value, password.value)
  loading.value = false
  if (success) {
    router.push('/login')
  }
}
</script>

<style scoped>
.create-user-view {
  max-width: 320px;
  margin: 4rem auto;
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

button {
  margin-top: 1rem;
  padding: 0.6rem;
  background-color: #2c5282;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
