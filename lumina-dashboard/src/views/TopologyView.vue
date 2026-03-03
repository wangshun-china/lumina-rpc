<template>
  <div class="glass-panel p-6 card-hover">
    <div class="flex items-center justify-between mb-4">
      <div>
        <h3 class="text-lg font-semibold text-white">服务拓扑</h3>
        <p class="text-xs text-slate-500">可视化 RPC 服务依赖关系</p>
      </div>
      <div class="flex items-center space-x-2">
        <button
          @click="fetchInstances"
          :disabled="loading"
          class="p-2 text-slate-400 hover:text-white transition-colors rounded-lg hover:bg-slate-800"
          title="刷新"
        >
          <svg class="w-5 h-5" :class="{ 'animate-spin': loading }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
        </button>
        <button
          @click="toggleFullscreen"
          class="p-2 text-slate-400 hover:text-white transition-colors rounded-lg hover:bg-slate-800"
          title="全屏"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 8V4m0 0h4M4 4l5 5m11-1V4m0 0h-4m4 0l-5 5M4 16v4m0 0h4m-4 0l5-5m11 5l-5-5m5 5v-4m0 4h-4" />
          </svg>
        </button>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="h-[500px] bg-slate-950/50 rounded-lg border border-slate-800 flex items-center justify-center">
      <div class="text-center">
        <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-cyan-500"></div>
        <p class="text-slate-400 mt-2">加载拓扑数据...</p>
      </div>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="h-[500px] bg-slate-950/50 rounded-lg border border-slate-800 flex items-center justify-center">
      <div class="text-center">
        <svg class="w-12 h-12 text-red-500 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <p class="text-red-400">{{ error }}</p>
        <button @click="fetchInstances" class="mt-4 px-4 py-2 bg-cyan-600 text-white rounded-lg hover:bg-cyan-500 transition-colors">
          重试
        </button>
      </div>
    </div>

    <!-- 拓扑图 -->
    <div v-else class="h-[500px] bg-slate-950/50 rounded-lg border border-slate-800 overflow-hidden">
      <VueFlow
        v-model:nodes="nodes"
        v-model:edges="edges"
        class="w-full h-full"
        :default-zoom="1"
        :min-zoom="0.1"
        :max-zoom="4"
        :fit-view="true"
        :snap-to-grid="true"
        @edge-click="onEdgeClick"
      >
        <Background pattern-color="#475569" :gap="16" />
        <Controls />
        <MiniMap />
      </VueFlow>
    </div>

    <!-- 图例 -->
    <div v-if="!loading && !error" class="flex items-center justify-center space-x-6 text-sm text-slate-400 flex-wrap">
      <div class="flex items-center space-x-2">
        <div class="w-3 h-3 rounded-full bg-emerald-500"></div>
        <span>Control Plane</span>
      </div>
      <div class="flex items-center space-x-2">
        <div class="w-3 h-3 rounded-full bg-blue-500"></div>
        <span>Provider</span>
      </div>
      <div class="flex items-center space-x-2">
        <div class="w-3 h-3 rounded-full bg-violet-500"></div>
        <span>Consumer</span>
      </div>
      <div class="flex items-center space-x-2">
        <div class="w-3 h-3 rounded-full bg-yellow-500"></div>
        <span>RPC 调用</span>
      </div>
      <div class="flex items-center space-x-2">
        <div class="w-3 h-3 rounded-full bg-emerald-400" style="border: 1px dashed #8b5cf6;"></div>
        <span>服务发现</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import axios from 'axios'
import { ElMessage } from 'element-plus'
// VueFlow 样式 - 必须导入
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'

const router = useRouter()

interface ServiceInstance {
  id: number
  serviceName: string
  instanceId: string
  host: string
  port: number
  status: string
  version?: string
  lastHeartbeat?: string
  registeredAt?: string
  expiresAt?: string
}

// 消费者服务名称（用于识别 Consumer）
const consumerServices = ['com.lumina.sample.command.service.CommandService']

const instances = ref<ServiceInstance[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// Vue Flow 节点和边 - 使用简单的 ref
const nodes = ref<any[]>([])
const edges = ref<any[]>([])

// 构建拓扑图数据
const buildTopology = (data: ServiceInstance[]) => {
  const newNodes: any[] = []
  const newEdges: any[] = []

  // 添加 Control Plane 中心节点
  newNodes.push({
    id: 'control-plane',
    type: 'default',
    label: 'Control Plane\u003cbr\u003e<span class="text-xs text-slate-400">服务注册/发现</span>',
    position: { x: 400, y: 180 },
    style: {
      background: '#059669',
      border: '2px solid #10b981',
      color: '#ffffff',
      fontSize: '14px',
      fontWeight: 'bold',
      width: 150,
    },
    sourcePosition: 'right',
    targetPosition: 'left',
  })

  // 如果没有实例，显示 Control Plane 和 Consumer
  if (data.length === 0) {
    // 添加 Consumer 节点（即使没有 Provider 也显示）
    newNodes.push({
      id: 'consumer-command',
      type: 'default',
      label: 'Command\u003cbr\u003e<span class="text-xs text-slate-400">Consumer</span>',
      position: { x: 400, y: 380 },
      style: {
        background: '#8b5cf6',
        border: '2px solid #a78bfa',
        color: '#ffffff',
        fontSize: '14px',
        width: 120,
      },
    })
    // 虚线连接
    newEdges.push({
      id: 'edge-consumer-cp',
      source: 'consumer-command',
      target: 'control-plane',
      type: 'smoothstep',
      style: {
        stroke: '#8b5cf6',
        strokeWidth: 2,
        strokeDasharray: '5 5',
      },
    })
    nodes.value = newNodes
    edges.value = newEdges
    return
  }

  // 区分 Provider 和 Consumer
  const providerInstances = data.filter(instance => {
    return !consumerServices.includes(instance.serviceName)
  })

  // 按服务名分组 (Provider)
  const providerGroups = providerInstances.reduce((groups, instance) => {
    const name = instance.serviceName
    if (!groups[name]) {
      groups[name] = []
    }
    groups[name].push(instance)
    return groups
  }, {} as Record<string, ServiceInstance[]>)

  // 计算 Provider 节点位置（圆形分布在上半部分）
  const providerNames = Object.keys(providerGroups)
  const providerRadius = 160
  const providerCount = providerNames.length
  const angleStep = providerCount > 0 ? Math.PI / (providerCount + 1) : 0

  providerNames.forEach((serviceName, index) => {
    const serviceInstances = providerGroups[serviceName]
    const healthyInstances = serviceInstances.filter(i => i.status === 'UP')
    const isHealthy = healthyInstances.length > 0

    // 角度：从 -π/2 (顶部) 开始，分布在上半圆
    const angle = -Math.PI / 2 + angleStep * (index + 1)
    const x = 400 + providerRadius * Math.cos(angle)
    const y = 180 + providerRadius * Math.sin(angle) * 0.5

    // 简化服务名用于显示
    const displayName = serviceName.split('.').pop() || serviceName

    newNodes.push({
      id: `provider-${serviceName}`,
      type: 'default',
      label: `${displayName}\u003cbr\u003e<span class="text-xs text-slate-400">${healthyInstances.length}/${serviceInstances.length} 健康</span>`,
      position: { x, y },
      style: {
        background: isHealthy ? '#3b82f6' : '#ef4444',
        border: `2px solid ${isHealthy ? '#60a5fa' : '#f87171'}`,
        color: '#ffffff',
        fontSize: '14px',
        width: 120,
      },
      sourcePosition: 'right',
      targetPosition: 'left',
    })

    // Provider 连接到 Control Plane（注册）
    newEdges.push({
      id: `edge-cp-${serviceName}`,
      source: 'control-plane',
      target: `provider-${serviceName}`,
      animated: isHealthy,
      style: {
        stroke: isHealthy ? '#10b981' : '#ef4444',
        strokeWidth: 2,
      },
    })
  })

  // 添加 Consumer 节点（固定在底部）
  newNodes.push({
    id: 'consumer-command',
    type: 'default',
    label: 'Command\u003cbr\u003e<span class="text-xs text-slate-400">Consumer</span>',
    position: { x: 400, y: 380 },
    style: {
      background: '#8b5cf6',
      border: '2px solid #a78bfa',
      color: '#ffffff',
      fontSize: '14px',
      width: 120,
    },
    sourcePosition: 'right',
    targetPosition: 'left',
  })

  // Consumer 虚线连接到 Control Plane（获取服务列表）
  newEdges.push({
    id: 'edge-consumer-cp',
    source: 'consumer-command',
    target: 'control-plane',
    type: 'smoothstep',
    style: {
      stroke: '#8b5cf6',
      strokeWidth: 2,
      strokeDasharray: '5 5',
    },
  })

  // Consumer 到 Provider 的连线（RPC 调用 - 橙色实线）
  providerNames.forEach(serviceName => {
    const displayName = serviceName.split('.').pop() || serviceName
    newEdges.push({
      id: `edge-rpc-${serviceName}`,
      source: 'consumer-command',
      target: `provider-${serviceName}`,
      animated: true,
      type: 'smoothstep',
      style: {
        stroke: '#f59e0b',
        strokeWidth: 2,
      },
      label: 'RPC',
      labelStyle: { fill: '#f59e0b', fontSize: 10 },
      labelBgStyle: { fill: '#1e293b', fillOpacity: 0.8 },
    })
  })

  nodes.value = newNodes
  edges.value = newEdges
}

// 获取服务实例列表
const fetchInstances = async () => {
  loading.value = true
  error.value = null
  try {
    const response = await axios.get('/api/v1/registry/instances')
    instances.value = response.data || []
    // 构建拓扑图
    buildTopology(instances.value)
  } catch (err: any) {
    console.error('获取服务实例失败:', err)
    error.value = err.response?.data?.message || '获取服务实例失败，请检查网络连接'
    ElMessage.error(error.value)
    // 即使出错也显示 Control Plane
    buildTopology([])
  } finally {
    loading.value = false
  }
}

// 全屏切换
const toggleFullscreen = () => {
  const elem = document.querySelector('.glass-panel')
  if (!elem) return

  if (!document.fullscreenElement) {
    elem.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}

// 点击边线事件处理
const onEdgeClick = (event: any) => {
  const edge = event.edge
  if (!edge) return

  // 只处理 RPC 调用连线（橙色）
  if (edge.style?.stroke === '#f59e0b') {
    // 从边 ID 中提取服务名: edge-rpc-serviceName
    const edgeId = edge.id as string
    if (edgeId.startsWith('edge-rpc-')) {
      const serviceName = edgeId.replace('edge-rpc-', '')

      // 跳转到 Mock 规则页面，并带上服务名参数
      router.push({
        path: '/mock-rules',
        query: { service: serviceName }
      })

      ElMessage.success(`跳转到 ${serviceName} 的 Mock 规则配置`)
    }
  }
}

// 页面加载时获取数据
onMounted(() => {
  fetchInstances()
})
</script>

<style scoped>
.VueFlow {
  background-color: #020617;
}

.vf-node {
  font-family: 'Inter', sans-serif;
}

.vf-node.selected {
  outline: 2px solid #3b82f6;
  outline-offset: 2px;
}

.vf-handle {
  background-color: #06b6d4;
}

.vf-handle:hover {
  background-color: #0891b2;
}
</style>