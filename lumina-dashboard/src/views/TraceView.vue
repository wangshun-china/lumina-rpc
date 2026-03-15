<template>
  <div class="min-h-screen bg-slate-900 p-6">
    <!-- 页面标题 -->
    <div class="text-center mb-6">
      <h1 class="text-3xl font-bold text-white mb-2">
        <span class="text-gradient">链路追踪</span>
        <span class="text-sm font-normal text-slate-500 ml-3">DISTRIBUTED TRACING</span>
      </h1>
      <p class="text-slate-500">实时追踪 RPC 调用链路 · 瀑布图可视化</p>
    </div>

    <div class="grid grid-cols-1 xl:grid-cols-3 gap-6">
      <!-- 左侧：Trace 列表 -->
      <div class="glass-panel p-4 xl:col-span-1">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-semibold text-white">
            <span class="text-cyan-400">▸</span> Trace 列表
          </h3>
          <div class="flex items-center space-x-2">
            <el-button size="small" @click="fetchTraces" :loading="loading">
              <span class="text-xs">刷新</span>
            </el-button>
          </div>
        </div>

        <!-- Trace 列表 -->
        <div class="space-y-2 max-h-[calc(100vh-280px)] overflow-y-auto">
          <div v-if="loading" class="text-center py-8 text-slate-500">
            <span class="inline-block animate-pulse">加载中...</span>
          </div>

          <div v-else-if="traces.length === 0" class="text-center py-8 text-slate-500">
            <svg class="w-12 h-12 mx-auto mb-2 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
            <p>暂无追踪数据</p>
            <p class="text-xs mt-1">发起 RPC 调用后将自动记录</p>
          </div>

          <div
            v-else
            v-for="trace in traces"
            :key="trace.traceId"
            @click="selectTrace(trace.traceId)"
            class="p-3 rounded-lg cursor-pointer transition-all duration-200"
            :class="[
              selectedTraceId === trace.traceId
                ? 'bg-cyan-900/30 border border-cyan-500/50'
                : 'bg-slate-800/50 border border-slate-700 hover:border-slate-600'
            ]"
          >
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center space-x-2">
                <span :class="trace.hasError ? 'text-red-400' : 'text-green-400'" class="text-sm font-bold">
                  {{ trace.hasError ? '✗' : '✓' }}
                </span>
                <span class="text-cyan-400 text-sm font-mono">{{ trace.traceId }}</span>
              </div>
              <span class="text-xs text-slate-500">{{ trace.spanCount }} spans</span>
            </div>
            <div class="flex items-center justify-between text-xs">
              <span class="text-slate-400">{{ trace.serviceName }}</span>
              <span class="text-slate-500">{{ formatDuration(trace.totalDuration) }}</span>
            </div>
            <div class="text-xs text-slate-600 mt-1">
              {{ formatTime(trace.startTime) }}
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：Trace 详情（瀑布图） -->
      <div class="glass-panel p-4 xl:col-span-2">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-semibold text-white">
            <span class="text-green-400">▸</span> 调用链瀑布图
          </h3>
          <div v-if="selectedTraceId" class="flex items-center space-x-2">
            <span class="text-xs text-slate-500">Trace ID:</span>
            <span class="text-xs text-cyan-400 font-mono">{{ selectedTraceId }}</span>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-if="!selectedTrace" class="text-center py-16 text-slate-500">
          <svg class="w-16 h-16 mx-auto mb-4 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
          <p>选择一个 Trace 查看详情</p>
        </div>

        <!-- 瀑布图 -->
        <div v-else class="space-y-4">
          <!-- 统计信息 -->
          <div class="grid grid-cols-4 gap-4 mb-6">
            <div class="bg-slate-800/50 rounded-lg p-3 text-center">
              <div class="text-2xl font-bold text-cyan-400">{{ selectedTrace.spanCount }}</div>
              <div class="text-xs text-slate-500">Span 数量</div>
            </div>
            <div class="bg-slate-800/50 rounded-lg p-3 text-center">
              <div class="text-2xl font-bold text-green-400">{{ formatDuration(selectedTrace.totalDuration) }}</div>
              <div class="text-xs text-slate-500">总耗时</div>
            </div>
            <div class="bg-slate-800/50 rounded-lg p-3 text-center">
              <div :class="selectedTrace.hasError ? 'text-red-400' : 'text-green-400'" class="text-2xl font-bold">
                {{ selectedTrace.hasError ? '失败' : '成功' }}
              </div>
              <div class="text-xs text-slate-500">状态</div>
            </div>
            <div class="bg-slate-800/50 rounded-lg p-3 text-center">
              <div class="text-2xl font-bold text-purple-400">{{ uniqueServices }}</div>
              <div class="text-xs text-slate-500">服务数</div>
            </div>
          </div>

          <!-- 瀑布图时间轴 -->
          <div class="bg-slate-800/30 rounded-lg p-4">
            <!-- 时间刻度 -->
            <div class="flex items-center justify-between text-xs text-slate-500 mb-2 px-32">
              <span>0ms</span>
              <span>{{ Math.round(selectedTrace.totalDuration / 4) }}ms</span>
              <span>{{ Math.round(selectedTrace.totalDuration / 2) }}ms</span>
              <span>{{ Math.round(selectedTrace.totalDuration * 3 / 4) }}ms</span>
              <span>{{ selectedTrace.totalDuration }}ms</span>
            </div>

            <!-- Span 行 -->
            <div class="space-y-2">
              <div
                v-for="span in sortedSpans"
                :key="span.spanId"
                class="flex items-center group"
              >
                <!-- 服务名和方法名 -->
                <div class="w-32 flex-shrink-0 pr-3">
                  <div class="text-sm text-white truncate">{{ span.serviceName }}</div>
                  <div class="text-xs text-slate-500 truncate">{{ span.methodName }}</div>
                </div>

                <!-- 瀑布条 -->
                <div class="flex-1 relative h-8 bg-slate-900/50 rounded">
                  <!-- 时间轴背景线 -->
                  <div class="absolute inset-0 flex">
                    <div class="flex-1 border-r border-slate-800"></div>
                    <div class="flex-1 border-r border-slate-800"></div>
                    <div class="flex-1 border-r border-slate-800"></div>
                    <div class="flex-1"></div>
                  </div>

                  <!-- Span 条 -->
                  <div
                    class="absolute top-1 bottom-1 rounded transition-all duration-200 group-hover:opacity-80"
                    :class="[
                      span.success ? 'bg-green-500/70' : 'bg-red-500/70',
                      span.kind === 'CLIENT' ? 'bg-cyan-500/70' : 'bg-purple-500/70'
                    ]"
                    :style="getSpanStyle(span)"
                  >
                    <div class="h-full flex items-center px-2">
                      <span class="text-xs text-white font-medium">{{ span.duration }}ms</span>
                    </div>
                  </div>
                </div>

                <!-- 状态和类型 -->
                <div class="w-20 flex-shrink-0 pl-3 flex items-center space-x-2">
                  <span
                    class="text-xs px-1.5 py-0.5 rounded"
                    :class="span.kind === 'CLIENT' ? 'bg-cyan-900/50 text-cyan-400' : 'bg-purple-900/50 text-purple-400'"
                  >
                    {{ span.kind }}
                  </span>
                  <span v-if="!span.success" class="text-red-400 text-xs">✗</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Span 详情列表 -->
          <div class="mt-6">
            <h4 class="text-sm font-semibold text-slate-300 mb-3">Span 详情</h4>
            <div class="space-y-2 max-h-64 overflow-y-auto">
              <div
                v-for="span in sortedSpans"
                :key="span.spanId"
                class="bg-slate-800/50 rounded-lg p-3 border border-slate-700"
              >
                <div class="flex items-center justify-between mb-2">
                  <div class="flex items-center space-x-2">
                    <span class="text-cyan-400 text-sm font-mono">{{ span.spanId }}</span>
                    <span
                      class="text-xs px-1.5 py-0.5 rounded"
                      :class="span.kind === 'CLIENT' ? 'bg-cyan-900/50 text-cyan-400' : 'bg-purple-900/50 text-purple-400'"
                    >
                      {{ span.kind }}
                    </span>
                  </div>
                  <span :class="span.success ? 'text-green-400' : 'text-red-400'" class="text-sm">
                    {{ span.duration }}ms
                  </span>
                </div>
                <div class="grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <span class="text-slate-500">服务:</span>
                    <span class="text-slate-300 ml-1">{{ span.serviceName }}</span>
                  </div>
                  <div>
                    <span class="text-slate-500">方法:</span>
                    <span class="text-slate-300 ml-1">{{ span.methodName }}</span>
                  </div>
                  <div>
                    <span class="text-slate-500">远程地址:</span>
                    <span class="text-slate-300 ml-1">{{ span.remoteAddress || '-' }}</span>
                  </div>
                  <div>
                    <span class="text-slate-500">父 Span:</span>
                    <span class="text-slate-300 ml-1">{{ span.parentSpanId || '-' }}</span>
                  </div>
                </div>
                <div v-if="span.errorMessage" class="mt-2 text-xs text-red-400 bg-red-900/20 rounded p-2">
                  {{ span.errorMessage }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import axios from 'axios'

interface TraceSummary {
  traceId: string
  serviceName: string
  startTime: number
  spanCount: number
  totalDuration: number
  hasError: boolean
}

interface SpanEntity {
  id: number
  traceId: string
  spanId: string
  parentSpanId: string | null
  serviceName: string
  methodName: string
  kind: string
  startTime: number
  duration: number
  success: boolean
  errorMessage: string | null
  remoteAddress: string | null
}

interface TraceDetail {
  traceId: string
  spans: SpanEntity[]
  totalDuration: number
  spanCount: number
  hasError: boolean
}

const traces = ref<TraceSummary[]>([])
const selectedTraceId = ref<string | null>(null)
const selectedTrace = ref<TraceDetail | null>(null)
const loading = ref(false)

let refreshInterval: ReturnType<typeof setInterval> | null = null

// 计算唯一服务数
const uniqueServices = computed(() => {
  if (!selectedTrace.value) return 0
  const services = new Set(selectedTrace.value.spans.map(s => s.serviceName))
  return services.size
})

// 按 startTime 排序的 spans
const sortedSpans = computed(() => {
  if (!selectedTrace.value) return []
  return [...selectedTrace.value.spans].sort((a, b) => a.startTime - b.startTime)
})

// 获取 Trace 列表
const fetchTraces = async () => {
  loading.value = true
  try {
    const response = await axios.get('/api/v1/traces?limit=50')
    // 处理可能的响应格式
    if (response.data && Array.isArray(response.data)) {
      traces.value = response.data
    } else if (response.data && response.data.traces) {
      traces.value = response.data.traces
    } else {
      traces.value = []
    }
  } catch (error) {
    console.error('获取 Trace 列表失败:', error)
    traces.value = []
  } finally {
    loading.value = false
  }
}

// 选择 Trace
const selectTrace = async (traceId: string) => {
  selectedTraceId.value = traceId
  try {
    const response = await axios.get(`/api/v1/traces/${traceId}`)
    selectedTrace.value = response.data
  } catch (error) {
    console.error('获取 Trace 详情失败:', error)
    selectedTrace.value = null
  }
}

// 格式化时间
const formatTime = (timestamp: number): string => {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  return date.toLocaleTimeString()
}

// 格式化耗时
const formatDuration = (ms: number): string => {
  if (!ms) return '0ms'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

// 计算 Span 条的样式
const getSpanStyle = (span: SpanEntity): Record<string, string> => {
  if (!selectedTrace.value || selectedTrace.value.totalDuration === 0) {
    return { left: '0%', width: '0%' }
  }

  const totalDuration = selectedTrace.value.totalDuration
  const minStartTime = Math.min(...selectedTrace.value.spans.map(s => s.startTime))

  // 计算相对起始位置
  const startOffset = span.startTime - minStartTime
  const leftPercent = (startOffset / totalDuration) * 100

  // 计算宽度
  const widthPercent = Math.max((span.duration / totalDuration) * 100, 2) // 最小 2%

  return {
    left: `${Math.max(0, leftPercent)}%`,
    width: `${Math.min(widthPercent, 100 - leftPercent)}%`
  }
}

onMounted(() => {
  fetchTraces()
  // 每 5 秒刷新一次
  refreshInterval = setInterval(fetchTraces, 5000)
})

onUnmounted(() => {
  if (refreshInterval) clearInterval(refreshInterval)
})
</script>

<style scoped>
.text-gradient {
  background: linear-gradient(135deg, #00ff88, #00ccff, #ff00aa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.glass-panel {
  background: rgba(30, 41, 59, 0.7);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(71, 85, 105, 0.4);
  border-radius: 12px;
}

/* 滚动条样式 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.3);
}

::-webkit-scrollbar-thumb {
  background: rgba(100, 116, 139, 0.5);
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: rgba(100, 116, 139, 0.7);
}
</style>