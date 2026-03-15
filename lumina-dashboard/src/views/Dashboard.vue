<template>
  <div class="space-y-6">
    <!-- 作者签名横幅 -->
    <div class="glass-panel p-4 flex items-center justify-center border border-cyan-500/30 bg-gradient-to-r from-cyan-900/20 via-purple-900/20 to-blue-900/20">
      <div class="flex items-center space-x-4">
        <div class="text-center">
          <div class="text-xs text-slate-400 uppercase tracking-widest">Architect & Developer</div>
          <div class="text-xl font-bold text-white mt-1">
            <span class="text-cyan-400">王顺</span>
            <span class="text-slate-500 mx-2">|</span>
            <span class="text-purple-400">Wang Shun</span>
          </div>
        </div>
        <a
          href="https://github.com/xixi-box"
          target="_blank"
          class="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 transition-colors border border-slate-600 hover:border-cyan-500"
          title="访问 GitHub"
        >
          <svg class="w-6 h-6 text-white" fill="currentColor" viewBox="0 0 24 24">
            <path fill-rule="evenodd" clip-rule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z"/>
          </svg>
        </a>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
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

    <!-- 请求趋势图 -->
    <TrendChart />

    <!-- 服务拓扑 -->
    <TopologyView />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import StatsCard from '../components/StatsCard.vue'
import TrendChart from '../components/TrendChart.vue'
import TopologyView from './TopologyView.vue'

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