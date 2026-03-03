<template>
  <div class="glass-panel p-6 card-hover">
    <div class="flex items-center justify-between mb-4">
      <div class="p-3 rounded-lg" :class="bgClass">
        <svg class="w-6 h-6" :class="iconClass" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" :d="iconPath" />
        </svg>
      </div>
      <div class="text-right">
        <div class="text-2xl font-bold text-white">{{ value }}</div>
        <div class="text-xs text-slate-500">{{ label }}</div>
      </div>
    </div>

    <div class="flex items-center justify-between">
      <div class="text-sm text-slate-400">{{ subtitle }}</div>
      <div class="flex items-center text-sm">
        <span class="flex items-center space-x-1" :class="trendClass">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
          </svg>
          <span>{{ trend }}</span>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  value: string | number
  label: string
  subtitle: string
  trend: string
  trendUp?: boolean
  color?: 'cyan' | 'blue' | 'emerald' | 'purple' | 'orange'
  iconPath?: string
}

const props = withDefaults(defineProps<Props>(), {
  trendUp: true,
  color: 'cyan',
  iconPath: 'M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4',
})

const bgClass = computed(() => {
  const colors = {
    cyan: 'bg-cyan-500/20',
    blue: 'bg-blue-500/20',
    emerald: 'bg-emerald-500/20',
    purple: 'bg-purple-500/20',
    orange: 'bg-orange-500/20',
  }
  return colors[props.color]
})

const iconClass = computed(() => {
  const colors = {
    cyan: 'text-cyan-400',
    blue: 'text-blue-400',
    emerald: 'text-emerald-400',
    purple: 'text-purple-400',
    orange: 'text-orange-400',
  }
  return colors[props.color]
})

const trendClass = computed(() => {
  return props.trendUp
    ? 'text-emerald-500'
    : 'text-red-500'
})
</script>

<style scoped>
.glass-panel {
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(71, 85, 105, 0.5);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
}
</style>
