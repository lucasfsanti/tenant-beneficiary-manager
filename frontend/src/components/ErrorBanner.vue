<template>
  <div v-if="problem" class="error-banner" role="alert">
    <p class="error-banner__title">{{ problem.title || 'Erro' }}</p>
    <p v-if="problem.detail" class="error-banner__detail">{{ problem.detail }}</p>
    <ul v-if="problem.errors && problem.errors.length" class="error-banner__list">
      <li v-for="(fieldError, index) in problem.errors" :key="index">
        <strong>{{ fieldError.field }}:</strong> {{ fieldError.message }}
      </li>
    </ul>
  </div>
</template>

<script setup>
defineProps({
  problem: {
    type: Object,
    default: null
  }
})
</script>

<style scoped>
.error-banner {
  background-color: #fdecea;
  border: 1px solid #f5c2c0;
  color: #8a1f1f;
  border-radius: 4px;
  padding: 0.75rem 1rem;
  margin-bottom: 1rem;
}

.error-banner__title {
  font-weight: 600;
  margin: 0 0 0.25rem;
}

.error-banner__detail {
  margin: 0;
}

.error-banner__list {
  margin: 0.5rem 0 0;
  padding-left: 1.25rem;
}
</style>
