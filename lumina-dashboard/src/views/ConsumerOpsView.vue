<template>
  <div class="min-h-screen bg-slate-900 p-6">
    <!-- 页面标题 -->
    <div class="text-center mb-6">
      <h1 class="text-3xl font-bold text-white mb-2">
        <span class="text-gradient">消费者控制台</span>
        <span class="text-sm font-normal text-slate-500 ml-3">CONSUMER OPS CENTER</span>
      </h1>
      <p class="text-slate-500">动态服务发现 · 智能参数填充 · 实时调用追踪</p>
    </div>

    <div class="grid grid-cols-1 xl:grid-cols-2 gap-6 h-[calc(100vh-180px)]">
      <!-- 左侧：Command Center -->
      <div class="glass-panel p-6 flex flex-col overflow-hidden">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-semibold text-white">
            <span class="text-green-400">▸</span> Command Center
          </h3>
          <div class="flex items-center space-x-2">
            <span class="text-xs text-slate-500">自动刷新</span>
            <el-switch v-model="autoRefresh" size="small" />
          </div>
        </div>

        <!-- 服务和方法级联选择 -->
        <div class="space-y-4 mb-6">
          <!-- 服务选择 -->
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">
              <span class="text-cyan-400">◆</span> 目标服务
            </label>
            <el-select
              v-model="testForm.serviceName"
              placeholder="从注册中心选择服务..."
              class="w-full"
              @change="onTestServiceChange"
              filterable
              size="large"
            >
              <el-option
                v-for="service in services"
                :key="service.name"
                :label="service.name"
                :value="service.name"
              >
                <div class="flex items-center justify-between">
                  <span class="font-medium text-white">{{ service.name }}</span>
                  <span class="text-xs text-slate-500">{{ service.methodCount || 0 }} methods</span>
                </div>
              </el-option>
            </el-select>
          </div>

          <!-- 方法选择 -->
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">
              <span class="text-cyan-400">◆</span> 目标方法
            </label>
            <el-select
              v-model="testForm.methodName"
              placeholder="选择方法..."
              class="w-full"
              :disabled="!testForm.serviceName"
              @change="onTestMethodChange"
              size="large"
            >
              <el-option
                v-for="method in testMethods"
                :key="method.name"
                :label="method.name"
                :value="method.name"
              >
                <div class="flex flex-col">
                  <span class="font-medium text-white">{{ method.name }}</span>
                  <span class="text-xs text-slate-400">{{ formatSignature(method) }}</span>
                </div>
              </el-option>
            </el-select>
          </div>
        </div>

        <!-- 智能参数表单 -->
        <div class="flex-1 overflow-y-auto">
          <!-- 集群策略配置 -->
          <div v-if="testForm.serviceName" class="mb-4 p-3 bg-slate-800/30 rounded-lg border border-slate-700">
            <label class="block text-sm font-medium text-slate-300 mb-3">
              <span class="text-purple-400">⚙</span> 高级配置
            </label>
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="text-xs text-slate-500 mb-1 block">集群策略</label>
                <el-select v-model="testForm.cluster" size="small" class="w-full">
                  <el-option label="Failover (自动重试)" value="failover" />
                  <el-option label="Failfast (快速失败)" value="failfast" />
                  <el-option label="Failsafe (静默失败)" value="failsafe" />
                  <el-option label="Forking (并行调用)" value="forking" />
                </el-select>
              </div>
              <div>
                <label class="text-xs text-slate-500 mb-1 block">重试次数</label>
                <el-input-number v-model="testForm.retries" :min="0" :max="10" size="small" class="w-full" />
              </div>
            </div>

            <div class="mt-2 text-xs text-slate-500">
              <span v-if="testForm.cluster === 'failover'">💡 失败时自动切换其他服务器重试</span>
              <span v-else-if="testForm.cluster === 'failfast'">💡 快速失败，适合非幂等操作</span>
              <span v-else-if="testForm.cluster === 'failsafe'">💡 失败时静默处理，不影响主流程</span>
              <span v-else-if="testForm.cluster === 'forking'">💡 并行调用多服务器，取最快响应</span>
            </div>
          </div>

          <!-- 熔断器/限流器状态卡片 -->
          <div v-if="testForm.serviceName" class="mb-4 grid grid-cols-2 gap-3">
            <!-- 熔断器卡片 -->
            <div class="p-3 bg-slate-800/50 rounded-lg border border-slate-700 cursor-pointer hover:border-cyan-600 transition-colors" @click="openCircuitBreakerDialog">
              <div class="flex items-center justify-between mb-2">
                <span class="text-xs text-slate-400">⚡ 熔断器</span>
                <span :class="currentServiceProtection?.circuitBreakerEnabled ? 'text-amber-400' : 'text-green-400'" class="text-xs font-bold">
                  {{ currentServiceProtection?.circuitBreakerEnabled ? 'OPENING' : 'CLOSED' }}
                </span>
              </div>
              <div class="text-xs text-slate-500">
                <template v-if="currentServiceProtection?.circuitBreakerEnabled">
                  阈值 {{ currentServiceProtection.circuitBreakerThreshold }}% / 恢复 {{ (currentServiceProtection.circuitBreakerTimeout / 1000).toFixed(0) }}s
                </template>
                <template v-else>
                  未配置熔断器
                </template>
              </div>
              <div class="text-xs text-cyan-400 mt-1">点击配置 →</div>
            </div>
            <!-- 限流器卡片 -->
            <div class="p-3 bg-slate-800/50 rounded-lg border border-slate-700 cursor-pointer hover:border-cyan-600 transition-colors" @click="openRateLimiterDialog">
              <div class="flex items-center justify-between mb-2">
                <span class="text-xs text-slate-400">🚦 限流器</span>
                <span :class="currentServiceProtection?.rateLimiterEnabled ? 'text-amber-400' : 'text-green-400'" class="text-xs font-bold">
                  {{ currentServiceProtection?.rateLimiterEnabled ? 'OPENING' : 'CLOSED' }}
                </span>
              </div>
              <div class="text-xs text-slate-500">
                <template v-if="currentServiceProtection?.rateLimiterEnabled">
                  QPS {{ currentServiceProtection.rateLimiterPermits }}
                  <template v-if="rateLimiterStats.totalRequests > 0">
                    <span class="text-cyan-400 ml-1">| 通过 {{ rateLimiterStats.passed }} / 拒绝 {{ rateLimiterStats.rejected }}</span>
                    <span class="text-amber-400 ml-1">负载率 {{ rateLimiterLoadRate }}%</span>
                  </template>
                  <template v-else>
                    <span class="text-slate-600 ml-1">| 暂无请求</span>
                  </template>
                </template>
                <template v-else>
                  未配置限流器
                </template>
              </div>
              <div class="text-xs text-cyan-400 mt-1">点击配置 →</div>
            </div>
          </div>

          <!-- 熔断器配置对话框 -->
          <el-dialog v-model="showCircuitBreakerDialog" title="⚡ 熔断器配置" width="500px" class="protection-dialog">
            <div class="space-y-4">
              <div class="p-4 bg-slate-800/50 rounded-lg border border-slate-700">
                <div class="flex items-center justify-between mb-3">
                  <span class="text-sm font-medium text-white">启用熔断器</span>
                  <el-switch v-model="circuitBreakerForm.enabled" />
                </div>
                <div class="grid grid-cols-2 gap-3">
                  <div>
                    <label class="text-xs text-slate-500 mb-1 block">错误率阈值 (%)</label>
                    <el-input-number v-model="circuitBreakerForm.threshold" :min="10" :max="100" size="small" class="w-full" />
                  </div>
                  <div>
                    <label class="text-xs text-slate-500 mb-1 block">恢复时间 (ms)</label>
                    <el-input-number v-model="circuitBreakerForm.timeout" :min="5000" :max="300000" :step="5000" size="small" class="w-full" />
                  </div>
                </div>
                <div class="mt-3 text-xs text-slate-500">
                  💡 当最近100次请求的错误率超过阈值时触发熔断，熔断后等待恢复时间后进入半开状态
                </div>
              </div>
            </div>
            <template #footer>
              <el-button @click="showCircuitBreakerDialog = false">取消</el-button>
              <el-button type="primary" @click="saveCircuitBreakerConfig" :loading="savingCircuitBreaker">保存</el-button>
            </template>
          </el-dialog>

          <!-- 限流器配置对话框 -->
          <el-dialog v-model="showRateLimiterDialog" title="🚦 限流器配置" width="500px" class="protection-dialog">
            <div class="space-y-4">
              <div class="p-4 bg-slate-800/50 rounded-lg border border-slate-700">
                <div class="flex items-center justify-between mb-3">
                  <span class="text-sm font-medium text-white">启用限流器</span>
                  <el-switch v-model="rateLimiterForm.enabled" />
                </div>
                <div>
                  <label class="text-xs text-slate-500 mb-1 block">每秒请求数 (QPS)</label>
                  <el-input-number v-model="rateLimiterForm.permits" :min="1" :max="10000" :step="1" size="small" class="w-full" />
                </div>
                <div class="mt-3 text-xs text-slate-500">
                  💡 使用令牌桶算法限流，当QPS超过阈值时拒绝请求
                </div>
              </div>
            </div>
            <template #footer>
              <el-button @click="showRateLimiterDialog = false">取消</el-button>
              <el-button type="primary" @click="saveRateLimiterConfig" :loading="savingRateLimiter">保存</el-button>
            </template>
          </el-dialog>

          <div v-if="testParams.length > 0">
            <label class="block text-sm font-medium text-slate-300 mb-3">
              <span class="text-cyan-400">◆</span> 参数配置
              <span class="text-xs text-slate-500 ml-2">（根据元数据自动生成）</span>
            </label>
            <div class="space-y-3">
              <div
                v-for="(param, index) in testParams"
                :key="index"
                class="bg-slate-800/50 rounded-lg p-3 border border-slate-700"
              >
                <div class="flex items-center justify-between mb-2">
                  <div class="flex items-center space-x-2">
                    <span class="px-2 py-0.5 bg-cyan-900/50 text-cyan-400 text-xs rounded">
                      Arg[{{ index }}]
                    </span>
                    <span class="text-sm text-white font-medium">{{ param.name || `param${index}` }}</span>
                  </div>
                  <span class="text-xs text-slate-500 font-mono">{{ formatTypeName(param.type) }}</span>
                </div>
                <el-input
                  v-model="testForm.args[index]"
                  :placeholder="`输入 ${formatTypeName(param.type)} 类型值`"
                  class="w-full"
                  @input="onArgChange(index)"
                >
                  <template #prefix>
                    <span class="text-slate-500">›</span>
                  </template>
                </el-input>
              </div>
            </div>
          </div>

          <div v-else-if="testForm.methodName" class="text-center py-8 text-slate-500">
            <span class="text-2xl">∅</span>
            <p class="mt-2">该方法无需参数</p>
          </div>

          <div v-else class="text-center py-8 text-slate-500">
            <svg class="w-12 h-12 mx-auto mb-2 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
            <p>请先选择服务和方法</p>
            <p class="text-xs mt-1">系统将自动从元数据中加载参数信息</p>
          </div>
        </div>

        <!-- 执行按钮 -->
        <div class="mt-4 pt-4 border-t border-slate-700">
          <el-button
            type="primary"
            @click="sendTestRequest"
            :loading="sending"
            :disabled="!testForm.serviceName || !testForm.methodName"
            class="w-full"
            size="large"
          >
            <span v-if="sending">
              <span class="inline-block animate-pulse">◉</span> 执行中...
            </span>
            <span v-else>
              ⚡ 执 行 请 求
            </span>
          </el-button>
        </div>
      </div>

      <!-- 右侧：Simulated Terminal -->
      <div class="glass-panel p-0 flex flex-col overflow-hidden">
        <div class="flex items-center justify-between p-4 border-b border-slate-700 bg-slate-800/50">
          <h3 class="text-lg font-semibold text-white">
            <span class="text-green-400">▸</span> Simulated Terminal
          </h3>
          <div class="flex items-center space-x-3">
            <span class="text-xs text-slate-500">共 {{ requestStream.length }} 条记录</span>
            <el-button size="small" @click="clearRequestStream" type="danger" plain>
              🗑️ 清除日志
            </el-button>
          </div>
        </div>

        <!-- 终端内容区 -->
        <div class="flex-1 overflow-y-auto bg-black p-4 font-mono text-sm" ref="terminalRef">
          <!-- 空状态 -->
          <div v-if="requestStream.length === 0" class="text-center py-12 text-slate-600">
            <div class="text-4xl mb-4">_</div>
            <p>系统初始化成功...</p>
            <p class="text-xs mt-2">向 Provider 发送请求后将自动显示请求流</p>
          </div>

          <!-- 请求记录 -->
          <div
            v-for="(record, index) in requestStream"
            :key="index"
            class="mb-4 pb-4 border-b border-slate-800"
            :class="{ 'opacity-50': index > 0 && index > 9 }"
          >
            <!-- 请求头 -->
            <div class="flex items-center justify-between mb-2">
              <div class="flex items-center space-x-2">
                <span class="text-xs text-slate-500">[{{ record.timestamp }}]</span>
                <span :class="record.success ? 'text-green-400' : 'text-red-400'" class="font-bold">
                  {{ record.success ? '✓' : '✗' }}
                </span>
                <span class="text-cyan-400">{{ record.serviceName }}</span>
                <span class="text-slate-400">.</span>
                <span class="text-yellow-400">{{ record.methodName }}</span>
                <span v-if="record.traceId" class="text-xs px-1.5 py-0.5 bg-slate-700 text-blue-400 rounded font-mono">
                  🔗 {{ record.traceId }}
                </span>
              </div>
              <div class="flex items-center space-x-2">
                <span v-if="record.mocked" class="text-xs px-2 py-0.5 bg-amber-900/50 text-amber-400 rounded">
                  🎭 MOCKED
                </span>
                <span v-if="record.cluster" class="text-xs px-2 py-0.5 bg-purple-900/50 text-purple-400 rounded">
                  {{ record.cluster.toUpperCase() }}
                </span>
                <span class="text-xs text-slate-500">{{ record.duration }}ms</span>
              </div>
            </div>

            <!-- 请求参数 -->
            <div class="ml-4 mb-2">
              <div class="text-xs text-slate-500 mb-1">📤 REQUEST</div>
              <pre class="text-xs text-yellow-300 whitespace-pre-wrap json-highlight" v-html="formatJson(record.args)"></pre>
            </div>

            <!-- 响应结果 -->
            <div class="ml-4">
              <div class="text-xs text-slate-500 mb-1">📥 RESPONSE</div>
              <pre
                class="text-xs whitespace-pre-wrap json-highlight"
                :class="record.success ? 'text-green-300' : 'text-red-300'"
                v-html="formatJson(record.response)"
              ></pre>
            </div>
          </div>
        </div>

        <!-- 底部状态栏 -->
        <div class="p-2 bg-slate-800/50 border-t border-slate-700 flex items-center justify-between text-xs text-slate-500">
          <div class="flex items-center space-x-4">
            <span>TERM: xterm-256color</span>
            <span>ENCODING: UTF-8</span>
          </div>
          <div class="flex items-center space-x-2">
            <span v-if="lastRequestTime" class="text-slate-400">
              LAST: {{ lastRequestTime }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

interface ServiceInfo {
  name: string
  metadata: any
  methodCount?: number
}

interface MethodInfo {
  name: string
  parameterTypes: string[]
  parameters: any[]
  returnType: any
}

interface RequestRecord {
  serviceName: string
  methodName: string
  args: any
  response: any
  success: boolean
  duration: number
  timestamp: string
  mocked?: boolean
  cluster?: string
  retries?: number
  traceId?: string
}

const services = ref<ServiceInfo[]>([])
const testMethods = ref<MethodInfo[]>([])
const requestStream = ref<RequestRecord[]>([])
const terminalRef = ref<HTMLElement | null>(null)
const autoRefresh = ref(true)
const sending = ref(false)
const testResult = ref<any>(null)
const lastRequestTime = ref('')

const testForm = ref({
  serviceName: '',
  methodName: '',
  args: [] as string[],
  cluster: 'failover',
  retries: 3
})

// 当前服务的保护配置
const currentServiceProtection = ref<any>(null)

// 熔断器配置对话框
const showCircuitBreakerDialog = ref(false)
const savingCircuitBreaker = ref(false)
const circuitBreakerForm = ref({
  enabled: false,
  threshold: 50,
  timeout: 30000
})

// 限流器配置对话框
const showRateLimiterDialog = ref(false)
const savingRateLimiter = ref(false)
const rateLimiterForm = ref({
  enabled: false,
  permits: 100
})

// 限流器统计数据
const rateLimiterStats = ref({
  passed: 0,
  rejected: 0,
  totalRequests: 0
})

// 限流器负载率（计算得出）
const rateLimiterLoadRate = computed(() => {
  if (rateLimiterStats.value.totalRequests === 0) return 0
  return Math.round((rateLimiterStats.value.passed / rateLimiterStats.value.totalRequests) * 100)
})

let refreshInterval: ReturnType<typeof setInterval> | null = null

// 格式化方法签名
const formatSignature = (method: MethodInfo): string => {
  if (!method.parameterTypes || method.parameterTypes.length === 0) {
    return 'void'
  }
  return method.parameterTypes.join(', ')
}

// 格式化类型名
const formatTypeName = (type: string): string => {
  if (!type) return 'Object'
  const parts = type.split('.')
  return parts[parts.length - 1]
}

// 格式化 JSON 并添加语法高亮
const formatJson = (data: any): string => {
  if (data === null || data === undefined) return '<span class="json-null">null</span>'
  if (typeof data === 'string') {
    try {
      return syntaxHighlight(JSON.parse(data))
    } catch {
      return data
    }
  }
  try {
    return syntaxHighlight(data)
  } catch {
    return String(data)
  }
}

// JSON 语法高亮
const syntaxHighlight = (json: any): string => {
  const str = JSON.stringify(json, null, 2)
  return str.replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, (match) => {
    let cls = 'json-number'
    if (/^"/.test(match)) {
      if (/:$/.test(match)) {
        cls = 'json-key'
      } else {
        cls = 'json-string'
      }
    } else if (/true|false/.test(match)) {
      cls = 'json-boolean'
    } else if (/null/.test(match)) {
      cls = 'json-null'
    }
    return `<span class="${cls}">${match}</span>`
  })
}

// 获取服务列表
const fetchServices = async () => {
  try {
    const response = await axios.get('/api/v1/registry/instances')
    const instances = response.data || []
    const serviceMap = new Map<string, ServiceInfo>()

    for (const instance of instances) {
      if (!serviceMap.has(instance.serviceName)) {
        serviceMap.set(instance.serviceName, {
          name: instance.serviceName,
          metadata: instance.serviceMetadata ? JSON.parse(instance.serviceMetadata) : null
        })
      }
    }

    // 获取每个服务的元数据以计算方法数
    const serviceList = Array.from(serviceMap.values())
    for (const service of serviceList) {
      try {
        const metaResponse = await axios.get(`/api/v1/registry/metadata/${service.name}`)
        // 修复：后端返回 { services: [{ interfaceName, methods }] }
        const metaData = metaResponse.data
        if (metaData?.services && metaData.services.length > 0) {
          service.methodCount = metaData.services[0].methods?.length || 0
        } else {
          service.methodCount = 0
        }
      } catch {
        service.methodCount = 0
      }
    }

    services.value = serviceList
  } catch (error) {
    console.error('获取服务列表失败:', error)
  }
}

// 服务变更
const onTestServiceChange = async () => {
  testForm.value.methodName = ''
  testForm.value.args = []
  testMethods.value = []
  currentServiceProtection.value = null

  if (!testForm.value.serviceName) return

  try {
    const response = await axios.get(`/api/v1/registry/metadata/${testForm.value.serviceName}`)
    // 修复：后端返回 { services: [{ interfaceName, methods }] }
    const metadata = response.data
    if (metadata && metadata.services && metadata.services.length > 0) {
      const methods = metadata.services[0].methods || []
      testMethods.value = methods.map((m: any) => ({
        name: m.name,
        parameterTypes: m.parameterTypes || [],
        parameters: m.parameters || [],
        returnType: m.returnType
      }))
    }
  } catch (error) {
    console.error('获取服务元数据失败:', error)
  }

  // 加载当前服务的保护配置
  await loadCurrentServiceProtection()
}

// 加载当前服务的保护配置
const loadCurrentServiceProtection = async () => {
  if (!testForm.value.serviceName) return

  try {
    const response = await axios.get(`/api/v1/protection/configs/${encodeURIComponent(testForm.value.serviceName)}`)
    if (response.data) {
      currentServiceProtection.value = response.data
      // 更新表单数据
      circuitBreakerForm.value = {
        enabled: response.data.circuitBreakerEnabled || false,
        threshold: response.data.circuitBreakerThreshold || 50,
        timeout: response.data.circuitBreakerTimeout || 30000
      }
      rateLimiterForm.value = {
        enabled: response.data.rateLimiterEnabled || false,
        permits: response.data.rateLimiterPermits || 100
      }
      // 从后端获取统计数据
      rateLimiterStats.value = {
        passed: response.data.rateLimiterPassed || 0,
        rejected: response.data.rateLimiterRejected || 0,
        totalRequests: (response.data.rateLimiterPassed || 0) + (response.data.rateLimiterRejected || 0)
      }
    }
  } catch (error) {
    // 配置不存在，使用默认值
    currentServiceProtection.value = null
    circuitBreakerForm.value = { enabled: false, threshold: 50, timeout: 30000 }
    rateLimiterForm.value = { enabled: false, permits: 100 }
    rateLimiterStats.value = { passed: 0, rejected: 0, totalRequests: 0 }
  }
}

// 方法变更 - 智能默认值填充
const onTestMethodChange = () => {
  testForm.value.args = testParams.value.map((param, index) => {
    const typeName = param.type?.toLowerCase() || ''
    const paramName = param.name?.toLowerCase() || ''

    // EngineService 默认参数
    if (testForm.value.serviceName?.includes('engine') || testForm.value.serviceName?.includes('Engine')) {
      if (paramName.includes('ship') || paramName.includes('id')) {
        return 'USS-1701'
      }
      if (paramName.includes('sector') || paramName.includes('region')) {
        return 'Alpha-7'
      }
      if (paramName.includes('temperature') || typeName.includes('double') || typeName.includes('float')) {
        return '25.0'
      }
    }

    // RadarService 默认参数
    if (testForm.value.serviceName?.includes('radar') || testForm.value.serviceName?.includes('Radar')) {
      if (paramName.includes('sector') || paramName.includes('region') || paramName.includes('zone')) {
        return 'Alpha-7'
      }
      if (paramName.includes('ship') || paramName.includes('id')) {
        return 'USS-1701'
      }
    }

    // 通用的参数默认值
    if (paramName.includes('ship') || paramName.includes('shipid') || paramName.includes('id')) {
      return 'USS-ENTERPRISE-NCC-1701'
    }
    if (paramName.includes('sector') || paramName.includes('region') || paramName.includes('zone')) {
      return 'Alpha-7'
    }
    if (paramName.includes('temperature') || typeName.includes('double') || typeName.includes('float')) {
      return '25.0'
    }
    if (typeName.includes('boolean')) {
      return 'true'
    }
    if (typeName.includes('int') || typeName.includes('long')) {
      return '0'
    }

    return ''
  })
}

// 参数输入变更
const onArgChange = (index: number) => {
  // 可以在这里添加实时验证等逻辑
}

const testParams = computed(() => {
  const method = testMethods.value.find(m => m.name === testForm.value.methodName)
  return method?.parameters || []
})

// 辅助函数：把 args 数组转换成对象展示（利用 method 元数据）
const buildDisplayArgs = (args: any[]): any => {
  const params = testParams.value
  const displayObj: Record<string, any> = {}

  args.forEach((arg, index) => {
    const param = params[index]
    if (param?.name) {
      // 使用参数名作为 key
      displayObj[param.name] = arg
    } else {
      // 没有参数名时使用索引
      displayObj[`arg${index}`] = arg
    }
  })

  return displayObj
}

// 发送测试请求
const sendTestRequest = async () => {
  if (!testForm.value.serviceName || !testForm.value.methodName) {
    ElMessage.warning('请选择服务和方法')
    return
  }

  sending.value = true
  const startTime = Date.now()

  try {
    // 构建参数 Map：严格按照 parameters 数组的 index 顺序
    const args: any[] = []

    testForm.value.args.forEach((arg, index) => {
      const param = testParams.value[index]
      if (!param) {
        args.push(null)
        return
      }

      if (!arg && arg !== '') {
        args.push(null)
        return
      }

      // 类型转换
      try {
        // 尝试解析 JSON
        args.push(JSON.parse(arg))
      } catch {
        // 根据类型转换
        const paramType = param.type?.toLowerCase() || ''
        if (paramType.includes('integer') || paramType.includes('int')) {
          const parsed = parseInt(arg)
          args.push(isNaN(parsed) ? null : parsed)
        } else if (paramType.includes('long')) {
          const parsed = parseInt(arg)
          args.push(isNaN(parsed) ? null : parsed)
        } else if (paramType.includes('double') || paramType.includes('float')) {
          const parsed = parseFloat(arg)
          args.push(isNaN(parsed) ? null : parsed)
        } else if (paramType.includes('boolean')) {
          args.push(arg.toLowerCase() === 'true')
        } else if (paramType.includes('string')) {
          args.push(arg)
        } else {
          // 尝试作为通用对象
          args.push(arg)
        }
      }
    })

    // 使用数组格式发送请求，严格按照 index 顺序
    const response = await axios.post('/api/command/proxy-invoke', {
      serviceName: testForm.value.serviceName,
      methodName: testForm.value.methodName,
      args: args
    })

    const duration = Date.now() - startTime
    const result = response.data

    lastRequestTime.value = new Date().toLocaleTimeString()

    // 优化日志展示：把 args 数组转换成对象显示（发给后端仍是数组）
    const displayArgs = buildDisplayArgs(args)

    // 添加到终端（使用 unshift 添加到列表开头）
    // 关键：使用深拷贝打断响应式引用，防止旧日志被污染
    const record: RequestRecord = {
      serviceName: testForm.value.serviceName,
      methodName: testForm.value.methodName,
      args: displayArgs,
      response: result.success ? result.data : result.error,
      success: result.success !== false,
      duration,
      timestamp: new Date().toLocaleTimeString(),
      mocked: result.mocked,
      cluster: testForm.value.cluster,
      retries: testForm.value.retries,
      traceId: result.traceId
    }

    // 安全地操作数组
    const newStream = [record, ...(Array.isArray(requestStream.value) ? requestStream.value : [])]
    // 限制保留最近 50 条记录
    requestStream.value = newStream.slice(0, 50)

    // 滚动到底部
    await nextTick()
    if (terminalRef.value) {
      terminalRef.value.scrollTop = 0
    }

    testResult.value = {
      success: result.success,
      data: result.data,
      duration
    }

    if (result.success) {
      ElMessage.success(`请求成功 (${duration}ms)`)
    } else {
      ElMessage.error(result.message || result.error || '请求失败')
    }
  } catch (error: any) {
    const duration = Date.now() - startTime
    lastRequestTime.value = new Date().toLocaleTimeString()

    const errorData = error.response?.data || error.message

    // 优化日志展示：把 args 数组转换成对象显示
    const displayArgs = buildDisplayArgs(testForm.value.args)

    // 添加到终端
    // 关键：使用深拷贝打断响应式引用，防止旧日志被污染
    const record: RequestRecord = {
      serviceName: testForm.value.serviceName,
      methodName: testForm.value.methodName,
      args: displayArgs,
      response: errorData,
      success: false,
      duration,
      timestamp: new Date().toLocaleTimeString(),
      traceId: error.response?.data?.traceId
    }

    const newStream = [record, ...(Array.isArray(requestStream.value) ? requestStream.value : [])]
    requestStream.value = newStream.slice(0, 50)

    await nextTick()
    if (terminalRef.value) {
      terminalRef.value.scrollTop = 0
    }

    testResult.value = {
      success: false,
      data: errorData,
      duration
    }

    ElMessage.error('请求失败: ' + (error.response?.data?.message || error.message))
  } finally {
    sending.value = false
  }
}

// 清空终端
const clearRequestStream = () => {
  requestStream.value = []
  testResult.value = null
  lastRequestTime.value = ''
}

// 定时刷新
const startAutoRefresh = () => {
  if (refreshInterval) clearInterval(refreshInterval)
  refreshInterval = setInterval(() => {
    if (autoRefresh.value) {
      fetchServices()
      // 刷新当前服务的保护配置
      if (testForm.value.serviceName) {
        loadCurrentServiceProtection()
      }
    }
  }, 1000)
}

// 打开熔断器配置弹窗
const openCircuitBreakerDialog = () => {
  if (!testForm.value.serviceName) {
    ElMessage.warning('请先选择服务')
    return
  }
  showCircuitBreakerDialog.value = true
}

// 打开限流器配置弹窗
const openRateLimiterDialog = () => {
  if (!testForm.value.serviceName) {
    ElMessage.warning('请先选择服务')
    return
  }
  showRateLimiterDialog.value = true
}

// 保存熔断器配置
const saveCircuitBreakerConfig = async () => {
  if (!testForm.value.serviceName) {
    ElMessage.warning('请先选择服务')
    return
  }

  savingCircuitBreaker.value = true
  try {
    await axios.put(`/api/v1/protection/configs/${encodeURIComponent(testForm.value.serviceName)}/circuit-breaker`, {
      enabled: circuitBreakerForm.value.enabled,
      threshold: circuitBreakerForm.value.threshold,
      timeout: circuitBreakerForm.value.timeout
    })

    ElMessage.success('熔断器配置已保存')
    showCircuitBreakerDialog.value = false
    await loadCurrentServiceProtection()
  } catch (error: any) {
    ElMessage.error('保存失败: ' + (error.response?.data?.message || error.message))
  } finally {
    savingCircuitBreaker.value = false
  }
}

// 保存限流器配置
const saveRateLimiterConfig = async () => {
  if (!testForm.value.serviceName) {
    ElMessage.warning('请先选择服务')
    return
  }

  savingRateLimiter.value = true
  try {
    await axios.put(`/api/v1/protection/configs/${encodeURIComponent(testForm.value.serviceName)}/rate-limiter`, {
      enabled: rateLimiterForm.value.enabled,
      permits: rateLimiterForm.value.permits
    })

    ElMessage.success('限流器配置已保存')
    showRateLimiterDialog.value = false
    await loadCurrentServiceProtection()
  } catch (error: any) {
    ElMessage.error('保存失败: ' + (error.response?.data?.message || error.message))
  } finally {
    savingRateLimiter.value = false
  }
}

onMounted(async () => {
  await fetchServices()
  startAutoRefresh()
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

/* 终端样式增强 */
.bg-black {
  background-color: #0a0a0a;
}

.terminal-glow {
  text-shadow: 0 0 5px currentColor;
}

/* JSON 语法高亮样式 */
.json-highlight {
  font-family: 'Fira Code', 'Consolas', monospace;
  line-height: 1.5;
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 4px;
  border-left: 2px solid;
}

.json-highlight .json-key {
  color: #7dd3fc;
}

.json-highlight .json-string {
  color: #86efac;
}

.json-highlight .json-number {
  color: #fcd34d;
}

.json-highlight .json-boolean {
  color: #f472b6;
}

.json-highlight .json-null {
  color: #a78bfa;
}
</style>