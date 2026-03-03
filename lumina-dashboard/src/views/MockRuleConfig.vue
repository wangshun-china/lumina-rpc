<template>
  <div class="glass-panel p-6 card-hover">
    <div class="flex items-center justify-between mb-4">
      <div>
        <h3 class="text-lg font-semibold text-white">Mock 规则配置</h3>
        <p class="text-xs text-slate-500">快速配置 RPC 响应拦截</p>
      </div>
    </div>

    <div class="space-y-4">
      <!-- 服务选择 -->
      <div>
        <label class="block text-sm font-medium text-slate-400 mb-2">
          <svg class="w-4 h-4 inline mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" />
          </svg>
          选择服务
        </label>
        <select v-model="selectedService" class="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-white focus:outline-none focus:ring-2 focus:ring-cyan-500">
          <option value="" disabled>请选择服务</option>
          <option value="user-service">user-service (用户服务)</option>
          <option value="order-service">order-service (订单服务)</option>
          <option value="inventory-service">inventory-service (库存服务)</option>
          <option value="payment-service">payment-service (支付服务)</option>
          <option value="email-service">email-service (邮件服务)</option>
        </select>
      </div>

      <!-- 方法选择 -->
      <div v-if="selectedService">
        <label class="block text-sm font-medium text-slate-400 mb-2">
          <svg class="w-4 h-4 inline mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21a4 4 0 01-4-4V5a2 2 0 012-2h4a2 2 0 012 2v12a4 4 0 01-4 4zm0 0h12a2 2 0 002-2v-4a2 2 0 00-2-2h-2.343M11 7.343l1.657-1.657a2 2 0 012.828 0l2.829 2.829a2 2 0 010 2.828l-8.486 8.485M7 17h.01" />
          </svg>
          方法名称
        </label>
        <select v-model="selectedMethod" class="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-white focus:outline-none focus:ring-2 focus:ring-cyan-500">
          <option value="" disabled>请选择方法</option>
          <option v-for="method in getMethodsByService(selectedService)" :key="method" :value="method">
            {{ method }}
          </option>
        </select>
      </div>

      <!-- 匹配条件 -->
      <div v-if="selectedMethod">
        <label class="block text-sm font-medium text-slate-400 mb-2">
          <svg class="w-4 h-4 inline mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z" />
          </svg>
          匹配类型
        </label>
        <select v-model="matchType" class="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-white focus:outline-none focus:ring-2 focus:ring-cyan-500">
          <option value="exact">精确匹配</option>
          <option value="partial">部分匹配</option>
          <option value="regex">正则表达式</option>
        </select>
      </div>

      <!-- 匹配条件 -->
      <div v-if="matchType && matchType !== 'exact'">
        <label class="block text-sm font-medium text-slate-400 mb-2">
          <svg class="w-4 h-4 inline mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          匹配条件
        </label>
        <input v-model="matchCondition" type="text" class="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-white focus:outline-none focus:ring-2 focus:ring-cyan-500" placeholder="请输入匹配条件" />
      </div>

      <!-- 响应类型 -->
      <div v-if="selectedMethod">
        <label class="block text-sm font-medium text-slate-400 mb-2">
          <svg class="w-4 h-4 inline mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 12h14M12 5l7 7-7 7" />
          </svg>
          响应类型
        </label>
        <select v-model="responseType" class="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-white focus:outline-none focus:ring-2 focus:ring-cyan-500">
          <option value="success">成功响应</option>
          <option value="error">错误响应</option>
          <option value="timeout">超时响应</option>
          <option value="custom">自定义响应</option>
        </select>
      </div>

      <!-- 响应延迟 -->
      <div v-if="selectedMethod">
        <label class="block text-sm font-medium text-slate-400 mb-2">
          <svg class="w-4 h-4 inline mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          响应延迟 (ms)
        </label>
        <input v-model="responseDelay" type="number" class="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-white focus:outline-none focus:ring-2 focus:ring-cyan-500" min="0" max="5000" placeholder="0" />
      </div>

      <!-- 响应状态码 -->
      <div v-if="selectedMethod">
        <label class="block text-sm font-medium text-slate-400 mb-2">
          <svg class="w-4 h-4 inline mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          HTTP 状态码
        </label>
        <input v-model="httpStatus" type="number" class="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-white focus:outline-none focus:ring-2 focus:ring-cyan-500" min="100" max="599" placeholder="200" />
      </div>

      <!-- 响应体 -->
      <div v-if="selectedMethod">
        <label class="block text-sm font-medium text-slate-400 mb-2">
          <svg class="w-4 h-4 inline mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
          响应体 (JSON)
        </label>
        <div class="relative">
          <textarea v-model="responseBody"
            class="w-full h-32 px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-sm text-white focus:outline-none focus:ring-2 focus:ring-cyan-500 font-mono resize-none"
            placeholder='{"code":200,"message":"success","data":{}}'
          />
        </div>
        <div class="flex items-center justify-between text-xs text-slate-500 mt-1">
          <span>{{ responseBody.length }} 字符</span>
          <button @click="formatJSON" class="text-cyan-500 hover:text-cyan-400 transition-colors">
            格式化
          </button>
        </div>
      </div>

      <!-- 提交按钮 -->
      <div v-if="selectedMethod" class="pt-4">
        <button @click="saveRule" :disabled="isSaving" :class="[
          'w-full py-3 px-4 rounded-lg font-medium text-sm transition-all duration-200 flex items-center justify-center space-x-2',
          isSaving
            ? 'bg-slate-700 text-slate-500 cursor-not-allowed'
            : 'bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 text-white shadow-lg hover:shadow-xl'
        ]">
          <span v-if="isSaving" class="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
          <span>{{ isSaving ? '保存中...' : '保存 Mock 规则' }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const selectedService = ref('')
const selectedMethod = ref('')
const matchType = ref('exact')
const matchCondition = ref('')
const responseType = ref('success')
const responseDelay = ref(0)
const httpStatus = ref(200)
const responseBody = ref('{"code":200,"message":"success","data":{}}')
const isSaving = ref(false)

const getMethodsByService = (service: string) => {
  const methodsMap: Record<string, string[]> = {
    'user-service': ['getUserInfo', 'createUser', 'updateUser', 'deleteUser', 'login', 'logout'],
    'order-service': ['createOrder', 'getOrder', 'updateOrder', 'cancelOrder', 'getOrders', 'confirmOrder'],
    'inventory-service': ['checkStock', 'getInventory', 'updateInventory', 'deductStock', 'addStock'],
    'payment-service': ['processPayment', 'refundPayment', 'validatePayment', 'getPaymentStatus', 'createPayment'],
    'email-service': ['sendEmail', 'sendTemplateEmail', 'verifyEmail', 'resendEmail', 'checkEmailStatus'],
  }
  return methodsMap[service] || []
}

const formatJSON = () => {
  try {
    responseBody.value = JSON.stringify(JSON.parse(responseBody.value), null, 2)
  } catch (error) {
    ElMessage.error('JSON 格式错误')
  }
}

const saveRule = async () => {
  isSaving.value = true

  try {
    const rule = {
      serviceName: selectedService.value,
      methodName: selectedMethod.value,
      matchType: matchType.value,
      matchCondition: matchType.value !== 'exact' ? matchCondition.value : null,
      responseType: responseType.value,
      responseBody: responseBody.value,
      responseDelayMs: parseInt(responseDelay.value.toString()),
      httpStatus: parseInt(httpStatus.value.toString()),
      enabled: true,
      priority: 0,
      description: `Mock rule for ${selectedService.value}.${selectedMethod.value}`,
      tags: 'mock',
    }

    await axios.post('/api/v1/rules', rule)

    ElMessage.success('Mock 规则已成功创建')

    // 清空表单
    setTimeout(() => {
      selectedService.value = ''
      selectedMethod.value = ''
      matchType.value = 'exact'
      matchCondition.value = ''
      responseType.value = 'success'
      responseDelay.value = 0
      httpStatus.value = 200
      responseBody.value = '{"code":200,"message":"success","data":{}}'
    }, 2000)
  } catch (error) {
    console.error('Error saving mock rule:', error)
    ElMessage.error('保存失败，请检查网络连接')
  } finally {
    isSaving.value = false
  }
}
</script>

<style scoped>
input::placeholder, textarea::placeholder {
  color: #64748b;
}

input:focus, textarea:focus, select:focus {
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}
</style>
