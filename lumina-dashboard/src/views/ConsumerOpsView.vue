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
              清空屏幕
            </el-button>
          </div>
        </div>

        <!-- 终端内容区 -->
        <div class="flex-1 overflow-y-auto bg-black p-4 font-mono text-sm" ref="terminalRef">
          <!-- 空状态 -->
          <div v-if="requestStream.length === 0" class="text-center py-12 text-slate-600">
            <div class="text-4xl mb-4">_</div>
            <p>等待系统初始化...</p>
            <p class="text-xs mt-2">启动 Provider 后将自动显示请求流</p>
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
              </div>
              <div class="flex items-center space-x-2">
                <span v-if="record.mocked" class="text-xs px-2 py-0.5 bg-amber-900/50 text-amber-400 rounded">
                  🎭 MOCKED
                </span>
                <span class="text-xs text-slate-500">{{ record.duration }}ms</span>
              </div>
            </div>

            <!-- 请求参数 -->
            <div class="ml-4 mb-2">
              <div class="text-xs text-slate-500 mb-1">📤 REQUEST</div>
              <pre class="text-xs text-yellow-300 whitespace-pre-wrap">{{ formatJson(record.args) }}</pre>
            </div>

            <!-- 响应结果 -->
            <div class="ml-4">
              <div class="text-xs text-slate-500 mb-1">📥 RESPONSE</div>
              <pre
                class="text-xs whitespace-pre-wrap"
                :class="record.success ? 'text-green-300' : 'text-red-300'"
              >{{ formatJson(record.response) }}</pre>
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
  args: [] as string[]
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

// 格式化 JSON
const formatJson = (data: any): string => {
  if (data === null || data === undefined) return 'null'
  if (typeof data === 'string') {
    try {
      return JSON.stringify(JSON.parse(data), null, 2)
    } catch {
      return data
    }
  }
  try {
    return JSON.stringify(data, null, 2)
  } catch {
    return String(data)
  }
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
        if (metaResponse.data?.methods) {
          service.methodCount = metaResponse.data.methods.length
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

  if (!testForm.value.serviceName) return

  try {
    const response = await axios.get(`/api/v1/registry/metadata/${testForm.value.serviceName}`)
    const metadata = response.data

    if (metadata && metadata.methods) {
      testMethods.value = metadata.methods.map((m: any) => ({
        name: m.name,
        parameterTypes: m.parameterTypes || [],
        parameters: m.parameters || [],
        returnType: m.returnType
      }))
    }
  } catch (error) {
    console.error('获取服务元数据失败:', error)
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

// 发送测试请求
const sendTestRequest = async () => {
  if (!testForm.value.serviceName || !testForm.value.methodName) {
    ElMessage.warning('请选择服务和方法')
    return
  }

  sending.value = true
  const startTime = Date.now()

  try {
    // 构建参数 Map：{ "sector": "Alpha-7", "shipId": "USS-1701" }
    const params: Record<string, any> = {}

    testForm.value.args.forEach((arg, index) => {
      const param = testParams.value[index]
      if (!param) return

      const paramName = param.name || `arg${index}`
      if (!arg && arg !== '') {
        params[paramName] = null
        return
      }

      // 类型转换
      try {
        // 尝试解析 JSON
        params[paramName] = JSON.parse(arg)
      } catch {
        // 根据类型转换
        if (param.type.includes('Integer') || param.type.includes('int')) {
          const parsed = parseInt(arg)
          params[paramName] = isNaN(parsed) ? null : parsed
        } else if (param.type.includes('Long') || param.type.includes('long')) {
          const parsed = parseInt(arg)
          params[paramName] = isNaN(parsed) ? null : parsed
        } else if (param.type.includes('Double') || param.type.includes('double')) {
          const parsed = parseFloat(arg)
          params[paramName] = isNaN(parsed) ? null : parsed
        } else if (param.type.includes('Boolean') || param.type.includes('boolean')) {
          params[paramName] = arg.toLowerCase() === 'true'
        } else {
          params[paramName] = arg
        }
      }
    })

    // 使用新的通用代理接口，发送 Map 格式
    const response = await axios.post('/api/command/proxy-invoke', {
      serviceName: testForm.value.serviceName,
      methodName: testForm.value.methodName,
      params: params
    })

    const duration = Date.now() - startTime
    const result = response.data

    lastRequestTime.value = new Date().toLocaleTimeString()

    // 添加到终端（使用 unshift 添加到列表开头）
    const record: RequestRecord = {
      serviceName: testForm.value.serviceName,
      methodName: testForm.value.methodName,
      args: params,
      response: result.success ? result.data : result.error,
      success: result.success !== false,
      duration,
      timestamp: new Date().toLocaleTimeString(),
      mocked: result.mocked
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

    // 添加到终端
    const record: RequestRecord = {
      serviceName: testForm.value.serviceName,
      methodName: testForm.value.methodName,
      args: testForm.value.args,
      response: errorData,
      success: false,
      duration,
      timestamp: new Date().toLocaleTimeString()
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
    }
  }, 5000)
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
</style>