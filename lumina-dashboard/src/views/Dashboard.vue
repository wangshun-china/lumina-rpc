<template>
  <div class="space-y-6">
    <!-- 页面标题 -->
    <div class="text-center relative">
      <h2 class="text-3xl font-bold text-white mb-2">
        <span class="text-gradient">Lumina-RPC 监控面板</span>
      </h2>
      <p class="text-slate-500">实时监控您的 RPC 服务与 Mock 规则运行状态</p>
      <!-- 手动刷新按钮 -->
      <button
        @click="fetchStats"
        :disabled="loading"
        class="absolute right-4 top-0 p-2 text-slate-400 hover:text-white transition-colors rounded-lg hover:bg-slate-800"
        title="刷新"
      >
        <svg class="w-5 h-5" :class="{ 'animate-spin': loading }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
        </svg>
      </button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      <div v-for="i in 4" :key="i" class="glass-panel p-6 card-hover animate-pulse">
        <div class="flex items-center justify-between">
          <div class="space-y-2">
            <div class="h-8 w-16 bg-slate-700 rounded"></div>
            <div class="h-4 w-24 bg-slate-700 rounded"></div>
          </div>
          <div class="h-12 w-12 bg-slate-700 rounded-lg"></div>
        </div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      <StatsCard
        :value="String(stats.onlineServices || 0)"
        label="在线服务"
        subtitle="活跃的 RPC 服务"
        :trend="formatTrend(stats.onlineServices, 0)"
        color="blue"
        :trendUp="(stats.onlineServices || 0) >= 0"
        iconPath="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"
      />

      <StatsCard
        :value="String(stats.enabledMockRules || 0)"
        label="Mock 规则"
        subtitle="已启用的规则数"
        :trend="formatTrend(stats.enabledMockRules, 0)"
        color="cyan"
        :trendUp="(stats.enabledMockRules || 0) >= 0"
        iconPath="M5 12h14M12 5l7 7-7 7"
      />

      <StatsCard
        :value="String(stats.totalInstances || 0)"
        label="运行实例"
        subtitle="健康的服务实例"
        trend="实时"
        color="emerald"
        :trendUp="true"
        iconPath="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
      />

      <StatsCard
        value="UP"
        label="系统状态"
        subtitle="Control Plane"
        trend="正常运行"
        color="purple"
        :trendUp="true"
        iconPath="M13 10V3L4 14h7v7l9-11h-7z"
      />
    </div>

    <!-- 服务拓扑 -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <!-- 拓扑图 -->
      <div class="lg:col-span-2">
        <TopologyView />
      </div>

      <!-- Mock 规则配置 -->
      <div class="lg:col-span-1">
        <MockRuleConfig />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import StatsCard from '../components/StatsCard.vue'
import TopologyView from './TopologyView.vue'
import MockRuleConfig from './MockRuleConfig.vue'

interface StatsData {
  onlineServices?: number
  enabledMockRules?: number
  totalInstances?: number
  totalMockRules?: number
  todayRequests?: number
  avgLatency?: number
  systemStatus?: string
  timestamp?: string
}

const stats = ref<StatsData>({})
const loading = ref(false)

// 获取统计数据
const fetchStats = async () => {
  loading.value = true
  try {
    const response = await axios.get('/api/v1/stats')
    stats.value = response.data || {}
  } catch (err: any) {
    console.error('获取统计数据失败:', err)
    ElMessage.error('获取统计数据失败')
    // 保持之前的统计数据或显示默认值
    stats.value = stats.value || {}
  } finally {
    loading.value = false
  }
}

// 格式化趋势文本
const formatTrend = (current: number | undefined, previous: number) => {
  if (current === undefined) return '无数据'
  const diff = current - previous
  if (diff === 0) return '持平'
  return diff > 0 ? `+${diff}` : `${diff}`
}

// 页面加载时获取数据
onMounted(() => {
  fetchStats()
})
</script>

<style scoped>
</style>
