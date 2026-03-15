<template>
  <div class="space-y-6">
    <!-- 页面标题 -->
    <div class="text-center">
      <h2 class="text-3xl font-bold text-white mb-2">
        <span class="text-gradient">服务保护配置</span>
      </h2>
      <p class="text-slate-500">熔断器 & 限流器 & 集群策略热更新</p>
    </div>

    <!-- 配置列表 -->
    <div class="glass-panel p-6 card-hover">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-lg font-semibold text-white">📋 已配置服务 ({{ configs.length }})</h3>
        <div class="flex items-center space-x-2">
          <button @click="openCreateDialog" class="px-4 py-2 bg-gradient-to-r from-cyan-600 to-blue-600 text-white text-sm rounded-lg hover:from-cyan-500 hover:to-blue-500 transition-colors flex items-center space-x-1">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            <span>新增配置</span>
          </button>
          <button @click="fetchConfigs" class="px-3 py-2 bg-slate-700 text-slate-300 text-sm rounded-lg hover:bg-slate-600 transition-colors">
            刷新
          </button>
        </div>
      </div>

      <!-- 配置表格 -->
      <div v-if="configs.length > 0" class="overflow-x-auto">
        <table class="w-full">
          <thead>
            <tr class="text-left text-xs text-slate-400 border-b border-slate-700">
              <th class="pb-3 font-medium">服务名称</th>
              <th class="pb-3 font-medium">熔断器</th>
              <th class="pb-3 font-medium">限流器</th>
              <th class="pb-3 font-medium">集群策略</th>
              <th class="pb-3 font-medium">超时/重试</th>
              <th class="pb-3 font-medium">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="config in configs" :key="config.id" class="border-b border-slate-800 hover:bg-slate-800/50">
              <td class="py-3">
                <div class="text-sm text-white font-medium">{{ config.serviceName }}</div>
                <div class="text-xs text-slate-500">v{{ config.version }}</div>
              </td>
              <td class="py-3">
                <div class="flex flex-col space-y-1">
                  <div class="flex items-center space-x-2">
                    <span :class="[
                      'px-2 py-0.5 rounded text-xs font-medium',
                      config.circuitBreakerEnabled ? 'bg-emerald-900/50 text-emerald-400' : 'bg-slate-700 text-slate-400'
                    ]">
                      {{ config.circuitBreakerEnabled ? '✓ 启用' : '✗ 禁用' }}
                    </span>
                  </div>
                  <div class="text-xs text-slate-400">
                    阈值: {{ config.circuitBreakerThreshold }}% | 恢复: {{ config.circuitBreakerTimeout }}ms
                  </div>
                  <div v-if="config.circuitBreakerState" class="text-xs">
                    <span :class="[
                      'px-1.5 py-0.5 rounded',
                      config.circuitBreakerState === 'CLOSED' ? 'bg-emerald-900/50 text-emerald-400' :
                      config.circuitBreakerState === 'OPEN' ? 'bg-red-900/50 text-red-400' :
                      'bg-amber-900/50 text-amber-400'
                    ]">
                      {{ config.circuitBreakerState }}
                    </span>
                  </div>
                </div>
              </td>
              <td class="py-3">
                <div class="flex flex-col space-y-1">
                  <div class="flex items-center space-x-2">
                    <span :class="[
                      'px-2 py-0.5 rounded text-xs font-medium',
                      config.rateLimiterEnabled ? 'bg-emerald-900/50 text-emerald-400' : 'bg-slate-700 text-slate-400'
                    ]">
                      {{ config.rateLimiterEnabled ? '✓ 启用' : '✗ 禁用' }}
                    </span>
                  </div>
                  <div class="text-xs text-slate-400">
                    限额: {{ config.rateLimiterPermits }}/s
                  </div>
                  <div v-if="config.rateLimiterPassed !== undefined" class="text-xs text-slate-500">
                    通过: {{ config.rateLimiterPassed }} | 拒绝: {{ config.rateLimiterRejected }}
                  </div>
                </div>
              </td>
              <td class="py-3">
                <span :class="[
                  'px-2 py-1 rounded text-xs font-medium',
                  config.clusterStrategy === 'failover' ? 'bg-blue-900/50 text-blue-400' :
                  config.clusterStrategy === 'failfast' ? 'bg-amber-900/50 text-amber-400' :
                  'bg-purple-900/50 text-purple-400'
                ]">
                  {{ config.clusterStrategy || 'failover' }}
                </span>
              </td>
              <td class="py-3 text-sm text-slate-400">
                <div>{{ config.timeoutMs || 0 }}ms</div>
                <div class="text-xs">{{ config.retries || 3 }} 次重试</div>
              </td>
              <td class="py-3">
                <div class="flex items-center space-x-2">
                  <button @click="editConfig(config)" class="text-xs text-cyan-400 hover:text-cyan-300">编辑</button>
                  <button @click="deleteConfig(config.serviceName)" class="text-xs text-red-400 hover:text-red-300">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 空状态 -->
      <div v-else class="text-center py-8 text-slate-500">
        <svg class="w-12 h-12 mx-auto mb-2 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
        </svg>
        <p>暂无保护配置</p>
      </div>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑保护配置' : '新增保护配置'"
      width="700px"
      :close-on-click-modal="false"
      class="protection-dialog"
    >
      <div class="space-y-6">
        <!-- 服务名称 -->
        <div>
          <label class="block text-sm font-medium text-slate-300 mb-2">服务名称</label>
          <el-select
            v-model="form.serviceName"
            placeholder="选择服务..."
            class="w-full"
            :disabled="isEditing"
            filterable
            allow-create
          >
            <el-option
              v-for="service in services"
              :key="service.name"
              :label="service.name"
              :value="service.name"
            />
          </el-select>
        </div>

        <!-- 熔断器配置 -->
        <div class="bg-slate-800/50 p-4 rounded-lg space-y-4">
          <div class="flex items-center justify-between">
            <h4 class="text-sm font-medium text-white flex items-center space-x-2">
              <span class="text-amber-400">⚡</span>
              <span>熔断器配置</span>
            </h4>
            <el-switch v-model="form.circuitBreakerEnabled" />
          </div>

          <div v-if="form.circuitBreakerEnabled" class="grid grid-cols-2 gap-4">
            <div>
              <label class="block text-xs text-slate-400 mb-1">错误率阈值 (%)</label>
              <el-input-number v-model="form.circuitBreakerThreshold" :min="1" :max="100" class="w-full" />
            </div>
            <div>
              <label class="block text-xs text-slate-400 mb-1">恢复时间 (ms)</label>
              <el-input-number v-model="form.circuitBreakerTimeout" :min="1000" :max="300000" :step="1000" class="w-full" />
            </div>
          </div>
        </div>

        <!-- 限流器配置 -->
        <div class="bg-slate-800/50 p-4 rounded-lg space-y-4">
          <div class="flex items-center justify-between">
            <h4 class="text-sm font-medium text-white flex items-center space-x-2">
              <span class="text-blue-400">🚦</span>
              <span>限流器配置</span>
            </h4>
            <el-switch v-model="form.rateLimiterEnabled" />
          </div>

          <div v-if="form.rateLimiterEnabled">
            <label class="block text-xs text-slate-400 mb-1">每秒请求数 (QPS)</label>
            <el-input-number v-model="form.rateLimiterPermits" :min="1" :max="100000" class="w-full" />
          </div>
        </div>

        <!-- 集群配置 -->
        <div class="bg-slate-800/50 p-4 rounded-lg space-y-4">
          <h4 class="text-sm font-medium text-white flex items-center space-x-2">
            <span class="text-purple-400">🔄</span>
            <span>集群容错配置</span>
          </h4>

          <div class="grid grid-cols-3 gap-4">
            <div>
              <label class="block text-xs text-slate-400 mb-1">集群策略</label>
              <el-select v-model="form.clusterStrategy" class="w-full">
                <el-option label="Failover (失败重试)" value="failover" />
                <el-option label="Failfast (快速失败)" value="failfast" />
                <el-option label="Failsafe (失败安全)" value="failsafe" />
              </el-select>
            </div>
            <div>
              <label class="block text-xs text-slate-400 mb-1">超时时间 (ms)</label>
              <el-input-number v-model="form.timeoutMs" :min="0" :max="60000" :step="100" class="w-full" />
            </div>
            <div>
              <label class="block text-xs text-slate-400 mb-1">重试次数</label>
              <el-input-number v-model="form.retries" :min="0" :max="10" class="w-full" />
            </div>
          </div>

          <div class="text-xs text-slate-500">
            💡 超时时间为 0 时使用注解默认值；重试次数仅在 Failover 策略下生效
          </div>
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end space-x-2">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveConfig" :loading="saving">
            {{ saving ? '保存中...' : '保存配置' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

interface ServiceInfo {
  name: string
  metadata: any
}

interface ProtectionConfig {
  id: number
  serviceName: string
  circuitBreakerEnabled: boolean
  circuitBreakerThreshold: number
  circuitBreakerTimeout: number
  circuitBreakerState?: string
  rateLimiterEnabled: boolean
  rateLimiterPermits: number
  rateLimiterPassed?: number
  rateLimiterRejected?: number
  clusterStrategy: string
  retries: number
  timeoutMs: number
  version: number
}

const configs = ref<ProtectionConfig[]>([])
const services = ref<ServiceInfo[]>([])
const dialogVisible = ref(false)
const isEditing = ref(false)
const saving = ref(false)

const form = ref({
  serviceName: '',
  circuitBreakerEnabled: true,
  circuitBreakerThreshold: 50,
  circuitBreakerTimeout: 30000,
  rateLimiterEnabled: false,
  rateLimiterPermits: 100,
  clusterStrategy: 'failover',
  retries: 3,
  timeoutMs: 0
})

// 获取配置列表
const fetchConfigs = async () => {
  try {
    const response = await axios.get('/api/v1/protection/configs')
    configs.value = response.data.configs || []
  } catch (error) {
    console.error('获取保护配置失败:', error)
    ElMessage.error('获取保护配置失败')
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
    services.value = Array.from(serviceMap.values())
  } catch (error) {
    console.error('获取服务列表失败:', error)
  }
}

// 打开创建对话框
const openCreateDialog = () => {
  isEditing.value = false
  form.value = {
    serviceName: '',
    circuitBreakerEnabled: true,
    circuitBreakerThreshold: 50,
    circuitBreakerTimeout: 30000,
    rateLimiterEnabled: false,
    rateLimiterPermits: 100,
    clusterStrategy: 'failover',
    retries: 3,
    timeoutMs: 0
  }
  dialogVisible.value = true
}

// 编辑配置
const editConfig = (config: ProtectionConfig) => {
  isEditing.value = true
  form.value = {
    serviceName: config.serviceName,
    circuitBreakerEnabled: config.circuitBreakerEnabled,
    circuitBreakerThreshold: config.circuitBreakerThreshold,
    circuitBreakerTimeout: config.circuitBreakerTimeout,
    rateLimiterEnabled: config.rateLimiterEnabled,
    rateLimiterPermits: config.rateLimiterPermits,
    clusterStrategy: config.clusterStrategy || 'failover',
    retries: config.retries || 3,
    timeoutMs: config.timeoutMs || 0
  }
  dialogVisible.value = true
}

// 保存配置
const saveConfig = async () => {
  if (!form.value.serviceName) {
    ElMessage.warning('请选择服务')
    return
  }

  saving.value = true
  try {
    // 先保存熔断器和限流器配置
    await axios.put(`/api/v1/protection/configs/${form.value.serviceName}/circuit-breaker`, {
      enabled: form.value.circuitBreakerEnabled,
      threshold: form.value.circuitBreakerThreshold,
      timeout: form.value.circuitBreakerTimeout
    })

    await axios.put(`/api/v1/protection/configs/${form.value.serviceName}/rate-limiter`, {
      enabled: form.value.rateLimiterEnabled,
      permits: form.value.rateLimiterPermits
    })

    // 保存集群配置
    await axios.put(`/api/v1/protection/configs/${form.value.serviceName}/cluster`, {
      timeoutMs: form.value.timeoutMs,
      retries: form.value.retries,
      clusterStrategy: form.value.clusterStrategy
    })

    ElMessage.success('配置保存成功，将在 5 秒内生效')
    dialogVisible.value = false
    await fetchConfigs()
  } catch (error: any) {
    console.error('保存配置失败:', error)
    ElMessage.error(error.response?.data?.message || '保存配置失败')
  } finally {
    saving.value = false
  }
}

// 删除配置
const deleteConfig = async (serviceName: string) => {
  try {
    await ElMessageBox.confirm(`确定要删除服务 "${serviceName}" 的保护配置吗？`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await axios.delete(`/api/v1/protection/configs/${serviceName}`)
    ElMessage.success('配置已删除')
    await fetchConfigs()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(async () => {
  await fetchServices()
  await fetchConfigs()
})
</script>

<style scoped>
.protection-dialog :deep(.el-dialog__body) {
  max-height: 70vh;
  overflow-y: auto;
}
</style>