<script setup>
import { ref, shallowRef, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, default: () => ({}) },
  height: { type: String, default: '300px' },
})

const el = ref(null)
const chart = shallowRef(null)
let resizeObserver = null

function render() {
  if (!el.value) return
  if (!chart.value) {
    chart.value = echarts.init(el.value, null, { renderer: 'canvas' })
  }
  chart.value.setOption(props.option, true)
}

onMounted(() => {
  render()
  resizeObserver = new ResizeObserver(() => {
    if (chart.value) chart.value.resize()
  })
  resizeObserver.observe(el.value)
})

onBeforeUnmount(() => {
  if (resizeObserver) resizeObserver.disconnect()
  if (chart.value) {
    chart.value.dispose()
    chart.value = null
  }
})

watch(
  () => props.option,
  () => render(),
  { deep: true }
)
</script>

<template>
  <div ref="el" class="echart" :style="{ height }" />
</template>

<style scoped>
.echart {
  width: 100%;
}
</style>
