<template>
  <div class="space-y-6">
    <!-- 页面标题 -->
    <div class="text-center">
      <h2 class="text-3xl font-bold text-white mb-2">
        <span class="text-gradient">服务管理</span>
      </h2>
      <p class="text-slate-500">管理与监控所有 RPC 服务实例</p>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="glass-panel p-8 text-center">
      <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-cyan-500"></div>
      <p class="text-slate-400 mt-2">加载服务实例中...</p>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="glass-panel p-8 text-center">
      <svg class="w-12 h-12 text-red-500 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <p class="text-red-400">{{ error }}</p>
      <button @click="fetchInstances" class="mt-4 px-4 py-2 bg-cyan-600 text-white rounded-lg hover:bg-cyan-500 transition-colors">
        重试
      </button>
    </div>

    <!-- 空状态 -->
    <div v-else-if="instances.length === 0" class="glass-panel p-8 text-center">
      <svg class="w-12 h-12 text-slate-600 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
      </svg>
      <p class="text-slate-400">暂无服务实例</p>
      <p class="text-slate-600 text-sm mt-1">当有服务注册到 Control Plane 时将显示在这里</p>
    </div>

    <!-- 按服务分组的卡片列表 -->
    <div v-else class="space-y-6">
      <div v-for="(serviceInstances, serviceName) in groupedInstances" :key="serviceName" class="glass-panel p-6 card-hover">
        <!-- 服务卡片头部 -->
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center space-x-3">
            <div class="w-3 h-3 rounded-full bg-cyan-500 animate-pulse"></div>
            <h3 class="text-lg font-semibold text-white">{{ serviceName }}</h3>
            <span class="text-xs text-slate-500 bg-slate-700 px-2 py-1 rounded">
              {{ serviceInstances.length }} 个实例
            </span>
          </div>
          <div class="flex items-center space-x-2">
            <!-- 查看元数据按钮 -->
            <button @click="viewMetadata(serviceName)"
                    class="px-3 py-1.5 bg-purple-600 text-white text-sm rounded-lg hover:bg-purple-500 transition-colors flex items-center space-x-1">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              <span>API 文档</span>
            </button>
            <!-- Mock 按钮 -->
            <button @click="openMockDialog(serviceName)"
                    class="px-3 py-1.5 bg-amber-600 text-white text-sm rounded-lg hover:bg-amber-500 transition-colors flex items-center space-x-1">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" />
              </svg>
              <span>Mock</span>
            </button>
            <button @click="toggleServiceExpand(serviceName)"
                    class="px-3 py-1.5 bg-slate-700 text-slate-300 text-sm rounded-lg hover:bg-slate-600 transition-colors">
              {{ expandedServices.has(serviceName) ? '收起' : '展开' }}
            </button>
          </div>
        </div>

        <!-- 实例列表（可折叠） -->
        <div v-show="expandedServices.has(serviceName)" class="space-y-2">
          <div v-for="instance in serviceInstances" :key="instance.id"
               class="flex items-center justify-between p-3 bg-slate-800/50 rounded-lg">
            <div class="flex items-center space-x-3">
              <div class="w-2 h-2 rounded-full animate-pulse"
                   :class="getStatusColor(instance.status, instance.expiresAt)"></div>
              <div>
                <div class="text-sm text-slate-300">{{ instance.host }}:{{ instance.port }}</div>
                <div class="text-xs text-slate-500">实例ID: {{ instance.instanceId?.substring(0, 8) }}...</div>
              </div>
            </div>
            <div class="flex items-center space-x-4">
              <div class="text-right">
                <div class="text-xs text-slate-500">状态</div>
                <div class="text-sm font-medium" :class="getStatusTextColor(instance.status, instance.expiresAt)">
                  {{ getDisplayStatus(instance) }}
                </div>
              </div>
              <div class="text-right">
                <div class="text-xs text-slate-500">心跳</div>
                <div class="text-sm text-slate-400">{{ formatTimeAgo(instance.lastHeartbeat) }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 已启用的 Mock 规则提示 -->
        <div v-if="getMockCountForService(serviceName) > 0" class="mt-3 p-2 bg-amber-900/30 rounded-lg flex items-center space-x-2">
          <svg class="w-4 h-4 text-amber-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span class="text-sm text-amber-400">
            已配置 {{ getMockCountForService(serviceName) }} 条 Mock 规则
          </span>
          <button @click="viewMockRules(serviceName)" class="text-xs text-amber-300 hover:text-amber-200 underline">
            查看详情
          </button>
        </div>
      </div>
    </div>

    <!-- 元数据抽屉 -->
    <el-drawer
      v-model="metadataDrawerVisible"
      title="服务 API 文档"
      direction="rtl"
      size="600px"
    >
      <div v-if="loadingMetadata" class="text-center py-8">
        <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-cyan-500"></div>
        <p class="text-slate-400 mt-2">加载元数据...</p>
      </div>

      <div v-else-if="currentMetadata" class="space-y-4">
        <!-- 服务信息 -->
        <div class="bg-slate-800/50 rounded-lg p-4">
          <div class="text-xs text-slate-500 mb-1">服务名称</div>
          <div class="text-lg font-semibold text-cyan-400">{{ currentMetadata.interfaceName }}</div>
        </div>

        <!-- 方法列表 -->
        <div>
          <h4 class="text-sm font-medium text-slate-300 mb-3">接口方法签名</h4>
          <div class="space-y-3">
            <div v-for="(method, index) in currentMetadata.methods" :key="index"
                 class="bg-slate-800/50 rounded-lg p-4 border-l-4 border-cyan-500">
              <div class="flex items-start justify-between">
                <div class="flex-1">
                  <!-- 方法名 -->
                  <div class="text-lg font-semibold text-white mb-2">{{ method.name }}</div>

                  <!-- 参数类型 -->
                  <div class="mb-2">
                    <span class="text-xs text-slate-500">参数：</span>
                    <code class="text-sm text-amber-400">
                      {{ method.parameterTypes?.length > 0 ? method.parameterTypes.join(', ') : '无参数' }}
                    </code>
                  </div>

                  <!-- 返回类型 -->
                  <div>
                    <span class="text-xs text-slate-500">返回：</span>
                    <code class="text-sm text-emerald-400">{{ method.returnType }}</code>
                  </div>
                </div>

                <!-- 快速测试按钮 -->
                <button @click="quickTest(currentMetadata.interfaceName, method.name)"
                        class="ml-4 px-3 py-1.5 bg-cyan-600 text-white text-xs rounded hover:bg-cyan-500 transition-colors">
                  ⚡ 测试
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 方法数量统计 -->
        <div class="text-center text-sm text-slate-500 pt-4 border-t border-slate-700">
          共 {{ currentMetadata.methods?.length || 0 }} 个公开方法
        </div>
      </div>

      <div v-else class="text-center py-8 text-slate-500">
        <svg class="w-12 h-12 mx-auto mb-2 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
        <p>暂无元数据</p>
        <p class="text-xs mt-1">服务启动后自动上报</p>
      </div>
    </el-drawer>

    <!-- Mock 规则配置对话框 -->
    <el-dialog v-model="mockDialogVisible" title="快速创建 Mock 规则" width="500px" :close-on-click-modal="false">
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-slate-300 mb-1">服务名称</label>
          <el-input v-model="mockForm.serviceName" disabled class="w-full" />
        </div>

        <div>
          <label class="block text-sm font-medium text-slate-300 mb-1">方法名称</label>
          <el-input v-model="mockForm.methodName" placeholder="输入方法名，或使用 * 匹配所有方法" class="w-full" />
        </div>

        <div>
          <label class="block text-sm font-medium text-slate-300 mb-1">引擎模式</label>
          <el-radio-group v-model="mockForm.mockType">
            <el-radio value="SHORT_CIRCUIT">⚡ 短路</el-radio>
            <el-radio value="TAMPER">🔄 篡改</el-radio>
          </el-radio-group>
        </div>

        <div>
          <label class="block text-sm font-medium text-slate-300 mb-1">Mock 数据 (JSON)</label>
          <el-input v-model="mockForm.responseBody" type="textarea" :rows="4"
                    placeholder='{"mocked": true}' />
        </div>

        <div class="flex items-center space-x-2">
          <el-switch v-model="mockForm.enabled" />
          <span class="text-sm text-slate-300">立即启用</span>
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end space-x-2">
          <el-button @click="mockDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveMockRule" :loading="savingMock">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const router = useRouter()

interface ServiceInstance {
  id: number
  serviceName: string
  instanceId: string
  host: string
  port: number
  status: string
  version?: string
  metadata?: string
  serviceMetadata?: string
  lastHeartbeat?: string
  registeredAt?: string
  expiresAt?: string
}

interface MethodInfo {
  name: string
  parameterTypes: string[]
  returnType: string
}

interface ServiceMetadata {
  interfaceName: string
  methods: MethodInfo[]
}

const instances = ref<ServiceInstance[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const expandedServices = ref<Set<string>>(new Set())
const mockRules = ref<Map<string, number>>(new Map())

// 元数据抽屉
const metadataDrawerVisible = ref(false)
const loadingMetadata = ref(false)
const currentMetadata = ref<ServiceMetadata | null>(null)

// Mock 对话框
const mockDialogVisible = ref(false)
const savingMock = ref(false)
const mockForm = ref({
  serviceName: '',
  methodName: '',
  mockType: 'SHORT_CIRCUIT',
  responseBody: '{}',
  enabled: true
})

// 按服务名分组
const groupedInstances = computed(() => {
  const grouped: Record<string, ServiceInstance[]> = {}
  for (const instance of instances.value) {
    if (!grouped[instance.serviceName]) {
      grouped[instance.serviceName] = []
    }
    grouped[instance.serviceName].push(instance)
  }
  return grouped
})

// 获取服务实例列表
const fetchInstances = async () => {
  loading.value = true
  error.value = null
  try {
    const response = await axios.get('/api/v1/registry/instances')
    instances.value = response.data || []
    instances.value.forEach(i => expandedServices.value.add(i.serviceName))
    await fetchMockStats()
  } catch (err: any) {
    console.error('获取服务实例失败:', err)
    error.value = err.response?.data?.message || '获取服务实例失败'
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

// 获取 Mock 规则统计
const fetchMockStats = async () => {
  try {
    const response = await axios.get('/api/v1/rules')
    const rules = response.data || []
    const counts = new Map<string, number>()
    rules.forEach((rule: any) => {
      if (rule.enabled) {
        counts.set(rule.serviceName, (counts.get(rule.serviceName) || 0) + 1)
      }
    })
    mockRules.value = counts
  } catch (err) {
    console.error('获取 Mock 规则统计失败:', err)
  }
}

// 查看元数据
const viewMetadata = async (serviceName: string) => {
  metadataDrawerVisible.value = true
  loadingMetadata.value = true
  currentMetadata.value = null

  try {
    const response = await axios.get(`/api/v1/registry/metadata/${serviceName}`)
    // 修复：后端返回 { services: [{ interfaceName, methods }] }
    const data = response.data
    if (data && data.services && data.services.length > 0) {
      currentMetadata.value = {
        interfaceName: data.services[0].interfaceName || serviceName,
        methods: data.services[0].methods || []
      }
    } else {
      currentMetadata.value = null
    }
  } catch (err) {
    console.error('获取元数据失败:', err)
    ElMessage.warning('该服务暂无元数据')
  } finally {
    loadingMetadata.value = false
  }
}

// 快速测试
const quickTest = (serviceName: string, methodName: string) => {
  metadataDrawerVisible.value = false
  router.push({
    path: '/consumer-ops',
    query: { service: serviceName, method: methodName }
  })
}

// 切换服务展开/收起
const toggleServiceExpand = (serviceName: string) => {
  if (expandedServices.value.has(serviceName)) {
    expandedServices.value.delete(serviceName)
  } else {
    expandedServices.value.add(serviceName)
  }
}

// 获取服务的 Mock 规则数量
const getMockCountForService = (serviceName: string) => {
  return mockRules.value.get(serviceName) || 0
}

// 打开 Mock 配置对话框
const openMockDialog = (serviceName: string) => {
  mockForm.value = {
    serviceName,
    methodName: '',
    mockType: 'SHORT_CIRCUIT',
    responseBody: '{}',
    enabled: true
  }
  mockDialogVisible.value = true
}

// 查看 Mock 规则详情
const viewMockRules = (serviceName: string) => {
  router.push('/mock-rules')
}

// 保存 Mock 规则
const saveMockRule = async () => {
  if (!mockForm.value.methodName) {
    ElMessage.warning('请输入方法名称')
    return
  }

  savingMock.value = true
  try {
    await axios.post('/api/v1/rules', {
      serviceName: mockForm.value.serviceName,
      methodName: mockForm.value.methodName,
      mockType: mockForm.value.mockType,
      responseType: 'success',
      responseBody: mockForm.value.responseBody,
      enabled: mockForm.value.enabled
    })

    ElMessage.success('Mock 规则已创建')
    mockDialogVisible.value = false
    await fetchMockStats()
  } catch (err: any) {
    ElMessage.error('创建失败: ' + (err.response?.data?.message || err.message))
  } finally {
    savingMock.value = false
  }
}

// 状态相关函数
const getStatusColor = (status: string, expiresAt?: string) => {
  if (status !== 'UP') return 'bg-red-500'
  if (expiresAt && new Date(expiresAt) < new Date()) return 'bg-yellow-500'
  return 'bg-emerald-500'
}

const getStatusTextColor = (status: string, expiresAt?: string) => {
  if (status !== 'UP') return 'text-red-500'
  if (expiresAt && new Date(expiresAt) < new Date()) return 'text-yellow-500'
  return 'text-emerald-500'
}

const getDisplayStatus = (instance: ServiceInstance) => {
  if (instance.status !== 'UP') return '已停止'
  if (instance.expiresAt && new Date(instance.expiresAt) < new Date()) return '过期'
  return '运行中'
}

const formatTimeAgo = (timeStr?: string) => {
  if (!timeStr) return 'N/A'
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return `${seconds}秒前`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  return `${hours}小时前`
}

onMounted(() => {
  fetchInstances()
  setInterval(fetchInstances, 30000)
})
</script>

<style scoped>
</style>