<template>
  <div class="space-y-6">
    <!-- 页面标题 -->
    <div class="text-center">
      <h2 class="text-3xl font-bold text-white mb-2">
        <span class="text-gradient">⚡ RPC 在线测试台</span>
      </h2>
      <p class="text-slate-500">直接从控制台调用微服务，无需编写客户端代码</p>
    </div>

    <!-- 主内容区 -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <!-- 左侧：请求配置 -->
      <div class="glass-panel p-6 card-hover">
        <h3 class="text-lg font-semibold text-white mb-4 flex items-center space-x-2">
          <span>🔧</span>
          <span>请求配置</span>
        </h3>

        <div class="space-y-4">
          <!-- 服务选择 -->
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">目标服务</label>
            <el-select
              v-model="selectedService"
              placeholder="选择服务..."
              class="w-full"
              @change="onServiceChange"
              filterable
            >
              <el-option
                v-for="service in services"
                :key="service.name"
                :label="service.name"
                :value="service.name"
              />
            </el-select>
          </div>

          <!-- 方法选择 -->
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">目标方法</label>
            <el-select
              v-model="selectedMethod"
              placeholder="选择方法..."
              class="w-full"
              :disabled="!selectedService"
              @change="onMethodChange"
            >
              <el-option
                v-for="method in availableMethods"
                :key="method.name"
                :label="method.name"
                :value="method.name"
              >
                <div class="flex flex-col">
                  <span class="font-medium">{{ method.name }}</span>
                  <span class="text-xs text-slate-400">{{ method.signature }}</span>
                </div>
              </el-option>
            </el-select>
          </div>

          <!-- 方法签名提示 -->
          <div v-if="currentMethodSignature" class="bg-slate-800/50 rounded-lg p-3">
            <div class="text-xs text-slate-400 mb-1">方法签名</div>
            <code class="text-sm text-cyan-400">{{ currentMethodSignature }}</code>
          </div>

          <!-- 参数输入 -->
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">
              请求参数 (JSON 数组格式)
            </label>
            <div class="bg-slate-800 rounded-lg overflow-hidden">
              <div ref="monacoContainer" class="h-48"></div>
            </div>
            <p class="text-xs text-slate-500 mt-1">
              例如：["Alpha-7"] 或 [{"sector": "Alpha-7"}]
            </p>
          </div>

          <!-- 超时设置 -->
          <div class="flex items-center space-x-4">
            <label class="text-sm font-medium text-slate-300">超时时间</label>
            <el-input-number
              v-model="timeout"
              :min="1000"
              :max="60000"
              :step="1000"
              class="w-32"
            />
            <span class="text-sm text-slate-400">ms</span>
          </div>

          <!-- 发送按钮 -->
          <button
            @click="sendRequest"
            :disabled="isSending || !selectedService || !selectedMethod"
            :class="[
              'w-full py-3 px-4 rounded-lg font-medium text-sm transition-all duration-200 flex items-center justify-center space-x-2',
              isSending || !selectedService || !selectedMethod
                ? 'bg-slate-700 text-slate-500 cursor-not-allowed'
                : 'bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-400 hover:to-orange-400 text-white shadow-lg hover:shadow-amber-500/25'
            ]"
          >
            <svg v-if="isSending" class="animate-spin w-5 h-5" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <span v-else>⚡</span>
            <span>{{ isSending ? '请求中...' : '发送 RPC 请求' }}</span>
          </button>
        </div>
      </div>

      <!-- 右侧：响应结果 -->
      <div class="glass-panel p-6 card-hover">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-lg font-semibold text-white flex items-center space-x-2">
            <span>📤</span>
            <span>响应结果</span>
          </h3>
          <div v-if="responseTime" class="text-sm text-slate-400">
            耗时: <span class="text-cyan-400">{{ responseTime }}ms</span>
          </div>
        </div>

        <!-- 状态指示 -->
        <div v-if="lastResponse" class="mb-4">
          <div :class="[
            'flex items-center space-x-2 px-3 py-2 rounded-lg',
            lastResponse.success ? 'bg-emerald-900/30 text-emerald-400' : 'bg-red-900/30 text-red-400'
          ]">
            <span v-if="lastResponse.success">✅</span>
            <span v-else>❌</span>
            <span class="text-sm font-medium">
              {{ lastResponse.success ? '调用成功' : '调用失败' }}
            </span>
          </div>
        </div>

        <!-- 响应内容 -->
        <div class="bg-slate-800/50 rounded-lg overflow-hidden">
          <div v-if="!lastResponse" class="p-8 text-center text-slate-500">
            <svg class="w-12 h-12 mx-auto mb-2 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 9l3 3-3 3m5 0h3M5 20h14a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            <p>发送请求后查看响应结果</p>
          </div>
          <div v-else class="p-4">
            <pre class="text-sm text-slate-300 overflow-auto max-h-96">{{ formattedResponse }}</pre>
          </div>
        </div>

        <!-- 错误堆栈 -->
        <div v-if="lastResponse && !lastResponse.success && lastResponse.error" class="mt-4">
          <div class="text-sm font-medium text-red-400 mb-2">错误信息</div>
          <div class="bg-red-900/20 rounded-lg p-3 border border-red-900/50">
            <pre class="text-xs text-red-300 overflow-auto">{{ lastResponse.error }}</pre>
          </div>
        </div>
      </div>
    </div>

    <!-- 历史记录 -->
    <div v-if="requestHistory.length > 0" class="glass-panel p-6 card-hover">
      <h3 class="text-lg font-semibold text-white mb-4">📜 调用历史</h3>
      <div class="space-y-2">
        <div
          v-for="(item, index) in requestHistory.slice(-5).reverse()"
          :key="index"
          class="flex items-center justify-between p-3 bg-slate-800/50 rounded-lg hover:bg-slate-800 transition-colors cursor-pointer"
          @click="loadFromHistory(item)"
        >
          <div class="flex items-center space-x-3">
            <span :class="item.success ? 'text-emerald-400' : 'text-red-400'">
              {{ item.success ? '✅' : '❌' }}
            </span>
            <span class="text-sm text-slate-300">{{ item.service }}.{{ item.method }}</span>
            <span class="text-xs text-slate-500">{{ item.args }}</span>
          </div>
          <div class="text-xs text-slate-500">{{ item.responseTime }}ms</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import * as monaco from 'monaco-editor'

interface ServiceInfo {
  name: string
  metadata: any
}

interface MethodInfo {
  name: string
  parameterTypes: string[]
  returnType: string
  signature: string
}

const services = ref<ServiceInfo[]>([])
const selectedService = ref('')
const selectedMethod = ref('')
const availableMethods = ref<MethodInfo[]>([])
const currentMethodSignature = ref('')
const timeout = ref(5000)
const isSending = ref(false)
const lastResponse = ref<any>(null)
const responseTime = ref<number | null>(null)
const requestHistory = ref<any[]>([])

// Monaco Editor
const monacoContainer = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null
const argsJson = ref('[]')

// 初始化 Monaco Editor
const initMonaco = () => {
  if (!monacoContainer.value) return

  editor = monaco.editor.create(monacoContainer.value, {
    value: argsJson.value,
    language: 'json',
    theme: 'vs-dark',
    minimap: { enabled: false },
    lineNumbers: 'off',
    scrollBeyondLastLine: false,
    fontSize: 13,
    tabSize: 2,
    automaticLayout: true,
  })

  editor.onDidChangeModelContent(() => {
    argsJson.value = editor?.getValue() || '[]'
  })
}

// 格式化响应
const formattedResponse = computed(() => {
  if (!lastResponse.value) return ''
  return JSON.stringify(lastResponse.value.data || lastResponse.value, null, 2)
})

// 获取服务列表
const fetchServices = async () => {
  try {
    const response = await axios.get('/api/v1/registry/instances')
    const instances = response.data || []

    // 去重并提取元数据
    const serviceMap = new Map<string, ServiceInfo>()
    for (const instance of instances) {
      if (!serviceMap.has(instance.serviceName)) {
        serviceMap.set(instance.serviceName, {
          name: instance.serviceName,
          metadata: instance.serviceMetadata ? JSON.parse(instance.serviceMetadata) : null
        })
      }
    }

    services.value = Array.from(serviceMap.values())
  } catch (error) {
    console.error('获取服务列表失败:', error)
  }
}

// 服务变更处理
const onServiceChange = async () => {
  selectedMethod.value = ''
  availableMethods.value = []
  currentMethodSignature.value = ''

  if (!selectedService.value) return

  // 获取服务元数据
  try {
    const response = await axios.get(`/api/v1/registry/metadata/${selectedService.value}`)
    const metadata = response.data

    if (metadata && metadata.methods) {
      availableMethods.value = metadata.methods.map((m: any) => ({
        name: m.name,
        parameterTypes: m.parameterTypes || [],
        returnType: m.returnType || 'void',
        signature: `${m.name}(${(m.parameterTypes || []).join(', ')}): ${m.returnType || 'void'}`
      }))
    }
  } catch (error) {
    console.error('获取服务元数据失败:', error)
  }
}

// 方法变更处理
const onMethodChange = () => {
  const method = availableMethods.value.find(m => m.name === selectedMethod.value)
  currentMethodSignature.value = method?.signature || ''

  // 根据参数类型生成示例参数
  if (method && method.parameterTypes.length > 0) {
    const sampleArgs = method.parameterTypes.map((type, index) => {
      if (type.includes('String')) return `arg${index}`
      if (type.includes('Integer') || type.includes('int')) return 0
      if (type.includes('Boolean') || type.includes('boolean')) return true
      return null
    })
    argsJson.value = JSON.stringify(sampleArgs, null, 2)
    if (editor) {
      editor.setValue(argsJson.value)
    }
  }
}

// 发送请求
const sendRequest = async () => {
  if (!selectedService.value || !selectedMethod.value) {
    ElMessage.warning('请选择服务和方法')
    return
  }

  // 解析参数
  let args: any[] = []
  try {
    args = JSON.parse(argsJson.value)
    if (!Array.isArray(args)) {
      throw new Error('参数必须是数组格式')
    }
  } catch (e: any) {
    ElMessage.error('参数 JSON 格式错误: ' + e.message)
    return
  }

  isSending.value = true
  const startTime = Date.now()

  try {
    const response = await axios.post('/api/v1/registry/invoke', {
      serviceName: selectedService.value,
      methodName: selectedMethod.value,
      args: args,
      timeout: timeout.value
    })

    const endTime = Date.now()
    responseTime.value = endTime - startTime
    lastResponse.value = response.data

    // 添加到历史记录
    requestHistory.value.push({
      service: selectedService.value,
      method: selectedMethod.value,
      args: argsJson.value,
      success: response.data.success,
      responseTime: responseTime.value
    })

    if (response.data.success) {
      ElMessage.success(`调用成功 (${responseTime.value}ms)`)
    } else {
      ElMessage.error('调用失败: ' + response.data.error)
    }

  } catch (error: any) {
    const endTime = Date.now()
    responseTime.value = endTime - startTime
    lastResponse.value = {
      success: false,
      error: error.response?.data?.error || error.message || '请求失败'
    }
    ElMessage.error('请求失败')
  } finally {
    isSending.value = false
  }
}

// 从历史记录加载
const loadFromHistory = (item: any) => {
  selectedService.value = item.service
  onServiceChange().then(() => {
    selectedMethod.value = item.method
    argsJson.value = item.args
    if (editor) {
      editor.setValue(argsJson.value)
    }
  })
}

onMounted(async () => {
  await fetchServices()
  nextTick(() => {
    initMonaco()
  })
})
</script>

<style scoped>
</style>