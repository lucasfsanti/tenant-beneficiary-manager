<template>
  <div v-if="totalPages > 1" class="pagination-control">
    <button class="button button--secondary" :disabled="page <= 0" @click="$emit('change', page - 1)">
      Anterior
    </button>
    <span>Página {{ page + 1 }} de {{ totalPages }}</span>
    <button
      class="button button--secondary"
      :disabled="page >= totalPages - 1"
      @click="$emit('change', page + 1)"
    >
      Próxima
    </button>
  </div>
</template>

<script setup>
defineProps({
  page: { type: Number, required: true },
  totalPages: { type: Number, required: true }
})
defineEmits(['change'])
</script>

<style scoped>
.pagination-control {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-top: 1rem;
}
</style>
