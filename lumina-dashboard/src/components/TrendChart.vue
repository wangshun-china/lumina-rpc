<template>
  <div class="glass-panel p-6 card-hover">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-lg font-semibold text-white">
        <span class="text-cyan-400">📈</span> 请求趋势
      </h3>
      <div class="flex items-center space-x-2">
        <el-select v-model="timeRange" size="small" @change="fetchTrendData" class="w-24">
          <el-option label="30分钟" :value="30" />
          <el-option label="60分钟" :value="60" />
          <el-option label="120分钟" :value="120" />
        </el-select>
        <button @click="fetchTrendData" class="p-1.5 text-slate-400 hover:text-white transition-colors rounded hover:bg-slate-700">
          <svg class="w-4 h-4" :class="{ 'animate-spin': loading }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
        </button>
      </div>
    </div>

    <div ref="chartRef" class="h-64"></div>

    <!-- 实时统计 -->
    <div class="mt-4 grid grid-cols-4 gap-4">
      <div class="text-center">
        <div class="text-2xl font-bold text-white">{{ realtimeStats.totalRequests }}</div>
        <div class="text-xs text-slate-500">总请求</div>
      </div>
      <div class="text-center">
        <div class="text-2xl font-bold text-green-400">{{ realtimeStats.successCount }}</div>
        <div class="text-xs text-slate-500">成功</div>
      </div>
      <div class="text-center">
        <div class="text-2xl font-bold text-red-400">{{ realtimeStats.failCount }}</div>
        <div class="text-xs text-slate-500">失败</div>
      </div>
      <div class="text-center">
        <div class="text-2xl font-bold text-cyan-400">{{ realtimeStats.avgLatency }}<span class="text-sm">ms</span></div>
        <div class="text-xs text-slate-500">平均延迟</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import axios from 'axios'

const chartRef = ref<HTMLElement | null>(null)
const timeRange = ref(30)
const loading = ref(false)

const realtimeStats = ref({
  totalRequests: 0,
  successCount: 0,
  failCount: 0,
  avgLatency: 0
})

let chart: echarts.ECharts | null = null
let refreshInterval: ReturnType<typeof setInterval> | null = null

const fetchTrendData = async () => {
  loading.value = true
  try {
    const response = await axios.get(`/api/v1/stats/trend?minutes=${timeRange.value}`)
    const trendData = response.data.trend || []

    updateChart(trendData)
  } catch (error) {
    console.error('获取趋势数据失败:', error)
  } finally {
    loading.value = false
  }
}

const fetchRealtimeStats = async () => {
  try {
    const response = await axios.get('/api/v1/stats/realtime')
    realtimeStats.value = response.data
  } catch (error) {
    // 静默失败
  }
}

const updateChart = (trendData: any[]) => {
  if (!chart) return

  // 如果没有数据，显示空状态
  if (!trendData || trendData.length === 0) {
    chart.setOption({
      backgroundColor: 'transparent',
      title: {
        text: '暂无请求数据',
        left: 'center',
        top: 'center',
        textStyle: { color: '#64748b', fontSize: 14 }
      }
    })
    return
  }

  const times = trendData.map((d: any) => {
    const time = new Date(d.time)
    return time.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  })
  const requests = trendData.map((d: any) => d.totalRequests || 0)
  const successes = trendData.map((d: any) => d.successCount || 0)
  const fails = trendData.map((d: any) => d.failCount || 0)
  const latencies = trendData.map((d: any) => d.avgLatency || 0)

  const option: echarts.EChartsOption = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(15, 23, 42, 0.9)',
      borderColor: 'rgba(100, 116, 139, 0.3)',
      textStyle: { color: '#e2e8f0' }
    },
    legend: {
      data: ['请求数', '成功', '失败', '延迟(ms)'],
      textStyle: { color: '#94a3b8' },
      top: 0
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: times,
      axisLine: { lineStyle: { color: '#334155' } },
      axisLabel: { color: '#64748b', fontSize: 10 }
    },
    yAxis: [
      {
        type: 'value',
        name: '请求数',
        axisLine: { lineStyle: { color: '#334155' } },
        axisLabel: { color: '#64748b' },
        splitLine: { lineStyle: { color: '#1e293b' } }
      },
      {
        type: 'value',
        name: '延迟(ms)',
        axisLine: { lineStyle: { color: '#334155' } },
        axisLabel: { color: '#64748b' },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '请求数',
        type: 'line',
        smooth: true,
        data: requests,
        lineStyle: { color: '#06b6d4', width: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(6, 182, 212, 0.3)' },
            { offset: 1, color: 'rgba(6, 182, 212, 0)' }
          ])
        },
        itemStyle: { color: '#06b6d4' }
      },
      {
        name: '成功',
        type: 'line',
        smooth: true,
        data: successes,
        lineStyle: { color: '#10b981', width: 2 },
        itemStyle: { color: '#10b981' }
      },
      {
        name: '失败',
        type: 'line',
        smooth: true,
        data: fails,
        lineStyle: { color: '#ef4444', width: 2 },
        itemStyle: { color: '#ef4444' }
      },
      {
        name: '延迟(ms)',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: latencies,
        lineStyle: { color: '#f59e0b', width: 2, type: 'dashed' },
        itemStyle: { color: '#f59e0b' }
      }
    ]
  }

  chart.setOption(option)
}

const initChart = () => {
  if (chartRef.value) {
    chart = echarts.init(chartRef.value)
    fetchTrendData()
  }
}

onMounted(() => {
  initChart()
  fetchRealtimeStats()

  // 定时刷新
  refreshInterval = setInterval(() => {
    fetchTrendData()
    fetchRealtimeStats()
  }, 5000)

  // 监听窗口大小变化
  window.addEventListener('resize', () => chart?.resize())
})

onUnmounted(() => {
  if (refreshInterval) clearInterval(refreshInterval)
  chart?.dispose()
  window.removeEventListener('resize', () => chart?.resize())
})
</script>

<style scoped>
</style>