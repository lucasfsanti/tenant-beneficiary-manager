<template>
  <div class="searchable-select" @focusout="close">
    <input
      v-model="query"
      type="text"
      class="searchable-select__input"
      :placeholder="placeholder"
      autocomplete="off"
      @focus="onFocus"
      @input="onInput"
      @keydown.down.prevent="moveHighlight(1)"
      @keydown.up.prevent="moveHighlight(-1)"
      @keydown.enter.prevent="selectHighlighted"
      @keydown.esc="close"
    />
    <ul v-if="isOpen" class="searchable-select__options">
      <li v-if="loading" class="searchable-select__status">Buscando...</li>
      <template v-else-if="options.length > 0">
        <li
          v-for="(option, index) in options"
          :key="option.id"
          class="searchable-select__option"
          :class="{ 'searchable-select__option--highlighted': index === highlightedIndex }"
          @mousedown.prevent="selectOption(option)"
        >
          {{ optionLabel(option) }}
        </li>
      </template>
      <li v-else class="searchable-select__status">Nenhum resultado encontrado.</li>
    </ul>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: null },
  search: { type: Function, required: true },
  optionLabel: { type: Function, required: true },
  initialLabel: { type: String, default: '' },
  placeholder: { type: String, default: 'Digite para buscar...' },
  debounceMs: { type: Number, default: 300 }
})
const emit = defineEmits(['update:modelValue'])

const query = ref(props.initialLabel || '')
const options = ref([])
const isOpen = ref(false)
const loading = ref(false)
const highlightedIndex = ref(-1)
let debounceHandle = null

watch(
  () => props.initialLabel,
  (label) => {
    query.value = label || ''
  }
)

function onFocus() {
  if (query.value) {
    runSearch()
  }
}

function onInput() {
  emit('update:modelValue', null)
  clearTimeout(debounceHandle)
  if (!query.value) {
    options.value = []
    isOpen.value = false
    return
  }
  debounceHandle = setTimeout(runSearch, props.debounceMs)
}

async function runSearch() {
  loading.value = true
  isOpen.value = true
  highlightedIndex.value = -1
  options.value = await props.search(query.value)
  loading.value = false
}

function selectOption(option) {
  query.value = props.optionLabel(option)
  emit('update:modelValue', option.id)
  close()
}

function selectHighlighted() {
  if (!isOpen.value || options.value.length === 0) return
  const index = highlightedIndex.value === -1 ? 0 : highlightedIndex.value
  selectOption(options.value[index])
}

function moveHighlight(delta) {
  if (!isOpen.value || options.value.length === 0) return
  const count = options.value.length
  highlightedIndex.value = (highlightedIndex.value + delta + count) % count
}

function close() {
  isOpen.value = false
  options.value = []
  highlightedIndex.value = -1
}
</script>

<style scoped>
.searchable-select {
  position: relative;
}

.searchable-select__input {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #ccc;
  border-radius: 4px;
}

.searchable-select__options {
  position: absolute;
  z-index: 10;
  top: 100%;
  left: 0;
  right: 0;
  margin: 0.25rem 0 0;
  padding: 0;
  list-style: none;
  max-height: 220px;
  overflow-y: auto;
  background: white;
  border: 1px solid #ccc;
  border-radius: 4px;
}

.searchable-select__option,
.searchable-select__status {
  padding: 0.5rem;
}

.searchable-select__option {
  cursor: pointer;
}

.searchable-select__option--highlighted,
.searchable-select__option:hover {
  background: #f0f0f0;
}

.searchable-select__status {
  color: #555;
}
</style>
