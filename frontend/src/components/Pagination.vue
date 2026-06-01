<script setup>
import { computed } from 'vue'

const props = defineProps({
  current: { type: Number, required: true },
  total: { type: Number, required: true },
  pageSize: { type: Number, default: 10 },
  pageSizes: { type: Array, default: () => [10, 20, 50, 100] },
})

const emit = defineEmits(['update:page', 'update:size'])

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

const rangeStart = computed(() => (props.current - 1) * props.pageSize + 1)
const rangeEnd = computed(() => Math.min(props.current * props.pageSize, props.total))

const pages = computed(() => {
  const tp = totalPages.value
  const cur = props.current
  const result = []

  if (tp <= 7) {
    for (let i = 1; i <= tp; i++) result.push(i)
    return result
  }

  result.push(1)
  if (cur > 3) result.push('...')

  const start = Math.max(2, cur - 1)
  const end = Math.min(tp - 1, cur + 1)
  for (let i = start; i <= end; i++) result.push(i)

  if (cur < tp - 2) result.push('...')
  result.push(tp)

  return result
})
</script>

<template>
  <div class="pagination">
    <span class="pagination-info">第 {{ rangeStart }}-{{ rangeEnd }} 条，共 {{ total }} 条</span>

    <button
      class="pagination-btn"
      :disabled="current <= 1"
      @click="$emit('update:page', current - 1)"
    >&laquo;</button>

    <template v-for="page in pages" :key="page">
      <span v-if="page === '...'" class="pagination-btn pagination-btn--ellipsis">&hellip;</span>
      <button
        v-else
        class="pagination-btn"
        :class="{ 'pagination-btn--active': page === current }"
        @click="$emit('update:page', page)"
      >{{ page }}</button>
    </template>

    <button
      class="pagination-btn"
      :disabled="current >= totalPages"
      @click="$emit('update:page', current + 1)"
    >&raquo;</button>

    <label class="pagination-size">
      <select :value="pageSize" @change="$emit('update:size', Number($event.target.value))">
        <option v-for="s in pageSizes" :key="s" :value="s">{{ s }} 条/页</option>
      </select>
    </label>
  </div>
</template>
