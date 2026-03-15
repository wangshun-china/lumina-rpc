<template>
  <div class="space-y-6">
    <!-- 页面标题 -->
    <div class="text-center">
      <h2 class="text-3xl font-bold text-white mb-2">
        <span class="text-gradient">Mock 规则配置</span>
      </h2>
      <p class="text-slate-500">双模 Mock 引擎：短路拦截 & 数据篡改</p>
    </div>

    <!-- 规则列表 -->
    <div class="glass-panel p-6 card-hover">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-lg font-semibold text-white">📋 已配置规则 ({{ rules.length }})</h3>
        <div class="flex items-center space-x-2">
          <button @click="openCreateDialog" class="px-4 py-2 bg-gradient-to-r from-cyan-600 to-blue-600 text-white text-sm rounded-lg hover:from-cyan-500 hover:to-blue-500 transition-colors flex items-center space-x-1">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            <span>新增规则</span>
          </button>
          <button @click="fetchRules" class="px-3 py-2 bg-slate-700 text-slate-300 text-sm rounded-lg hover:bg-slate-600 transition-colors">
            刷新
          </button>
        </div>
      </div>

      <!-- 规则表格 -->
      <div v-if="rules.length > 0" class="overflow-x-auto">
        <table class="w-full">
          <thead>
            <tr class="text-left text-xs text-slate-400 border-b border-slate-700">
              <th class="pb-3 font-medium">服务 / 方法</th>
              <th class="pb-3 font-medium">引擎模式</th>
              <th class="pb-3 font-medium">条件规则</th>
              <th class="pb-3 font-medium">延迟</th>
              <th class="pb-3 font-medium">状态</th>
              <th class="pb-3 font-medium">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="rule in rules" :key="rule.id" class="border-b border-slate-800 hover:bg-slate-800/50">
              <td class="py-3">
                <div class="text-sm text-white">{{ rule.serviceName }}</div>
                <div class="text-xs text-slate-500">{{ rule.methodName }}</div>
              </td>
              <td class="py-3">
                <span :class="[
                  'px-2 py-1 rounded text-xs font-medium',
                  rule.mockType === 'TAMPER' ? 'bg-purple-900/50 text-purple-400' : 'bg-amber-900/50 text-amber-400'
                ]">
                  {{ rule.mockType === 'TAMPER' ? '🔄 篡改' : '⚡ 短路' }}
                </span>
              </td>
              <td class="py-3">
                <span v-if="rule.conditionRule" class="text-xs text-cyan-400 font-mono">
                  {{ formatConditionRule(rule.conditionRule) }}
                </span>
                <span v-else class="text-xs text-slate-500">无条件</span>
              </td>
              <td class="py-3 text-sm text-slate-400">
                {{ rule.responseDelayMs || 0 }}ms
              </td>
              <td class="py-3">
                <el-switch
                  :model-value="rule.enabled"
                  @change="toggleRule(rule)"
                  size="small"
                />
              </td>
              <td class="py-3">
                <div class="flex items-center space-x-2">
                  <button @click="editRule(rule)" class="text-xs text-cyan-400 hover:text-cyan-300">编辑</button>
                  <button @click="deleteRule(rule.id)" class="text-xs text-red-400 hover:text-red-300">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 空状态 -->
      <div v-else class="text-center py-8 text-slate-500">
        <svg class="w-12 h-12 mx-auto mb-2 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
        <p>暂无 Mock 规则</p>
      </div>
    </div>

    <!-- 新增/编辑规则对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑 Mock 规则' : '新增 Mock 规则'"
      width="850px"
      :close-on-click-modal="false"
      class="mock-dialog"
    >
      <div class="space-y-5">
        <!-- 服务和方法选择 -->
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">目标服务</label>
            <el-select
              v-model="form.serviceName"
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
          <div>
            <label class="block text-sm font-medium text-slate-300 mb-2">目标方法</label>
            <el-select
              v-model="form.methodName"
              placeholder="选择方法..."
              class="w-full"
              :disabled="!form.serviceName"
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
        </div>

        <!-- 引擎模式 -->
        <div>
          <label class="block text-sm font-medium text-slate-300 mb-2">引擎模式</label>
          <el-radio-group v-model="form.mockType" class="w-full">
            <el-radio value="SHORT_CIRCUIT" class="!mb-2">
              <div class="flex items-center space-x-2">
                <span class="text-amber-400">⚡</span>
                <div>
                  <div class="font-medium">直接短路</div>
                  <div class="text-xs text-slate-400">不发起网络请求，直接返回 Mock 数据</div>
                </div>
              </div>
            </el-radio>
            <el-radio value="TAMPER" class="!mb-0">
              <div class="flex items-center space-x-2">
                <span class="text-purple-400">🔄</span>
                <div>
                  <div class="font-medium">数据篡改</div>
                  <div class="text-xs text-slate-400">先发起真实调用，再将 Mock 数据与真实响应合并</div>
                </div>
              </div>
            </el-radio>
          </el-radio-group>
        </div>

        <!-- 动态条件构造器 -->
        <div>
          <div class="flex items-center justify-between mb-2">
            <label class="text-sm font-medium text-slate-300">
              条件匹配规则
              <span class="text-xs text-slate-500 ml-2">（多条件 AND 关系）</span>
            </label>
            <el-button size="small" @click="addCondition" :disabled="!form.methodName">
              + 添加条件
            </el-button>
          </div>

          <div v-if="form.conditions.length > 0" class="space-y-2">
            <div
              v-for="(condition, index) in form.conditions"
              :key="index"
              class="flex items-center space-x-3 bg-slate-800/50 p-3 rounded-lg"
            >
              <!-- 参数索引选择 -->
              <el-select
                v-model="condition.paramIndex"
                placeholder="参数"
                class="w-44"
                @change="onConditionParamChange(condition)"
              >
                <el-option
                  v-for="(param, pIdx) in currentMethodParams"
                  :key="pIdx"
                  :label="`${pIdx}: ${formatParamName(param)}`"
                  :value="pIdx"
                >
                  <span class="text-cyan-400">{{ pIdx }}:</span>
                  <span class="ml-1">{{ formatParamName(param) }}</span>
                </el-option>
              </el-select>

              <!-- 操作符 -->
              <el-select v-model="condition.operator" class="w-32">
                <el-option label="等于 ==" value="equals" />
                <el-option label="不等于 !=" value="notEquals" />
                <el-option label="包含" value="contains" />
                <el-option label="开头为" value="startsWith" />
                <el-option label="结尾为" value="endsWith" />
                <el-option label="正则" value="regex" />
                <el-option label="大于 >" value="gt" />
                <el-option label="小于 <" value="lt" />
              </el-select>

              <!-- 匹配值 -->
              <el-input
                v-model="condition.value"
                placeholder="匹配值"
                class="flex-1"
                style="min-width: 200px;"
              />

              <!-- 删除按钮 -->
              <el-button
                type="danger"
                size="small"
                @click="removeCondition(index)"
                circle
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </el-button>
            </div>
          </div>
          <div v-else class="text-center py-4 text-slate-500 border border-dashed border-slate-700 rounded-lg">
            无条件触发（所有请求都会被 Mock）
          </div>
        </div>

        <!-- 响应延迟 -->
        <div>
          <label class="block text-sm font-medium text-slate-300 mb-2">响应延迟 (ms)</label>
          <el-input-number v-model="form.responseDelayMs" :min="0" :max="10000" :step="100" />
        </div>

        <!-- 动态 Mock 数据表单 -->
        <div>
          <div class="flex items-center justify-between mb-2">
            <label class="text-sm font-medium text-slate-300">
              Mock 响应数据
              <span v-if="form.mockType === 'TAMPER'" class="text-xs text-purple-400 ml-2">
                支持占位符
              </span>
            </label>
            <el-button size="small" @click="toggleJsonMode">
              {{ useJsonMode ? '切换到表单模式' : '切换到 JSON 模式' }}
            </el-button>
          </div>

          <!-- JSON 模式 -->
          <div v-if="useJsonMode" class="bg-slate-800 rounded-lg overflow-hidden">
            <div ref="monacoContainer" class="h-48"></div>
          </div>

          <!-- 表单模式 -->
          <div v-else class="space-y-3">
            <div v-if="returnTypeFields.length === 0" class="text-center py-4 text-slate-500 border border-dashed border-slate-700 rounded-lg">
              请先选择方法和查看返回类型字段
            </div>
            <div
              v-for="field in returnTypeFields"
              :key="field.name"
              class="flex items-center space-x-3 bg-slate-800/50 p-3 rounded-lg"
            >
              <div class="w-40">
                <span class="text-sm text-white">{{ field.name }}</span>
                <span class="text-xs text-slate-400 ml-2">{{ formatTypeName(field.type) }}</span>
              </div>
              <div class="flex-1">
                <el-input
                  v-model="form.responseFields[field.name]"
                  :placeholder="getFieldPlaceholder(field)"
                >
                  <template #suffix v-if="form.mockType === 'TAMPER'">
                    <el-tooltip content="使用 {{base}} 引用原始真实值" placement="top">
                      <span class="text-purple-400 cursor-pointer text-xs" v-text="'{{base}}'"></span>
                    </el-tooltip>
                  </template>
                </el-input>
              </div>
            </div>
          </div>

          <!-- TAMPER 模式提示 -->
          <div v-if="form.mockType === 'TAMPER'" class="mt-2 p-3 bg-purple-900/20 border border-purple-800/50 rounded-lg">
            <p class="text-xs text-purple-300">
              💡 <strong>占位符语法：</strong>
              <code class="bg-purple-900/50 px-1 rounded" v-text="'{{base}}'"></code> - 原始值 |
              <code class="bg-purple-900/50 px-1 rounded" v-text="'{{base.field}}'"></code> - 原始字段 |
              <code class="bg-purple-900/50 px-1 rounded" v-text="'{{base[0]}}'"></code> - 数组元素
            </p>
          </div>
        </div>

        <!-- 启用状态 -->
        <div class="flex items-center space-x-2">
          <el-switch v-model="form.enabled" />
          <span class="text-sm text-slate-300">立即启用</span>
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end space-x-2">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveRule" :loading="saving">
            {{ saving ? '保存中...' : '保存规则' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as monaco from 'monaco-editor'

interface ServiceInfo {
  name: string
  metadata: any
}

interface MethodInfo {
  name: string
  parameterTypes: string[]
  returnType: any
  parameters: any[]
  signature: string
  raw: any
}

interface Condition {
  paramIndex: number
  operator: string
  value: string
}

interface ReturnField {
  name: string
  type: string
  fields?: ReturnField[]
}

const route = useRoute()
const rules = ref<any[]>([])
const services = ref<ServiceInfo[]>([])
const availableMethods = ref<MethodInfo[]>([])
const dialogVisible = ref(false)
const isEditing = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const useJsonMode = ref(false)

const form = ref({
  serviceName: '',
  methodName: '',
  mockType: 'SHORT_CIRCUIT',
  conditions: [] as Condition[],
  responseDelayMs: 0,
  responseBody: '{}',
  responseFields: {} as Record<string, string>,
  enabled: true
})

// 当前方法的参数列表
const currentMethodParams = computed(() => {
  const method = availableMethods.value.find(m => m.name === form.value.methodName)
  return method?.parameters || []
})

// 当前方法的返回类型字段
const returnTypeFields = computed(() => {
  const method = availableMethods.value.find(m => m.name === form.value.methodName)
  if (!method?.returnType?.fields) return []
  return flattenFields(method.returnType.fields)
})

// 扁平化字段结构（支持嵌套对象）
const flattenFields = (fields: any, prefix = ''): ReturnField[] => {
  const result: ReturnField[] = []
  if (!fields || typeof fields !== 'object') {
    return result
  }

  for (const [name, info] of Object.entries(fields)) {
    const fieldInfo = info as any
    const fullName = prefix ? `${prefix}.${name}` : name

    // 如果是简单字段，直接添加
    if (!fieldInfo.fields || Object.keys(fieldInfo.fields).length === 0) {
      result.push({ name: fullName, type: fieldInfo.type })
    } else {
      // 嵌套字段，递归处理
      const nestedFields = flattenFields(fieldInfo.fields, fullName)
      result.push(...nestedFields)
    }
  }
  return result
}

// Monaco Editor
const monacoContainer = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null

const initMonaco = () => {
  if (!monacoContainer.value) return
  if (editor) editor.dispose()

  editor = monaco.editor.create(monacoContainer.value, {
    value: form.value.responseBody,
    language: 'json',
    theme: 'vs-dark',
    minimap: { enabled: false },
    lineNumbers: 'off',
    scrollBeyondLastLine: false,
    fontSize: 13,
    tabSize: 2,
    automaticLayout: true,
  })
}

// 格式化类型名
const formatTypeName = (type: string): string => {
  if (!type) return 'unknown'
  const parts = type.split('.')
  return parts[parts.length - 1]
}

// 获取字段占位符
const getFieldPlaceholder = (field: ReturnField): string => {
  if (form.value.mockType === 'TAMPER') {
    return `{{base.${field.name}}} 或输入新值`
  }
  return `输入 ${formatTypeName(field.type)} 类型的值`
}

// 格式化条件规则显示
const formatConditionRule = (rule: string): string => {
  if (!rule) return ''
  try {
    const parsed = JSON.parse(rule)
    if (Array.isArray(parsed)) {
      return parsed.map(c => `参数${c.index} ${c.operator || '=='} "${c.value}"`).join(' AND ')
    }
    return JSON.stringify(parsed)
  } catch {
    return rule
  }
}

// 格式化参数名称
const formatParamName = (param: any): string => {
  if (!param) return ''
  // 支持嵌套结构：{ name: 'shipId', type: 'String' } 或 { name: 'sector', type: 'String' }
  if (param.name && param.type) {
    return `${param.name} (${formatTypeName(param.type)})`
  }
  if (param.name) return param.name
  if (param.type) {
    // 简化类型名
    const type = param.type
    const parts = type.split('.')
    return parts[parts.length - 1]
  }
  return ''
}

// 切换 JSON 模式
const toggleJsonMode = () => {
  if (!useJsonMode.value) {
    // 切换到 JSON 模式，将表单数据转为 JSON
    form.value.responseBody = JSON.stringify(form.value.responseFields, null, 2)
    nextTick(() => initMonaco())
  } else {
    // 切换到表单模式，尝试解析 JSON
    try {
      const json = editor?.getValue() || form.value.responseBody
      form.value.responseFields = JSON.parse(json)
    } catch (e) {
      // 解析失败，清空
    }
  }
  useJsonMode.value = !useJsonMode.value
}

// 获取规则列表
const fetchRules = async () => {
  try {
    const response = await axios.get('/api/v1/rules')
    rules.value = response.data || []
  } catch (error) {
    console.error('获取规则列表失败:', error)
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

// 服务变更处理
const onServiceChange = async () => {
  form.value.methodName = ''
  form.value.conditions = []
  availableMethods.value = []

  if (!form.value.serviceName) return

  try {
    const response = await axios.get(`/api/v1/registry/metadata/${form.value.serviceName}`)
    // 修复：后端返回 { services: [{ interfaceName, methods }] }
    const metadata = response.data

    if (metadata && metadata.services && metadata.services.length > 0) {
      const methods = metadata.services[0].methods || []
      availableMethods.value = methods.map((m: any) => ({
        name: m.name,
        parameterTypes: m.parameterTypes || [],
        returnType: m.returnType || { type: 'void' },
        parameters: m.parameters || [],
        signature: `${m.name}(${(m.parameterTypes || []).join(', ')})`,
        raw: m
      }))
    }

    // 如果从拓扑图带参数过来，自动选中方法
    const methodFromQuery = route.query.method as string
    if (methodFromQuery && availableMethods.value.find(m => m.name === methodFromQuery)) {
      form.value.methodName = methodFromQuery
      onMethodChange()
    }
  } catch (error) {
    console.error('获取服务元数据失败:', error)
  }
}

// 方法变更处理
const onMethodChange = () => {
  // 初始化响应字段
  form.value.responseFields = {}

  // 提取返回类型字段
  const method = availableMethods.value.find(m => m.name === form.value.methodName)
  if (method?.returnType?.fields) {
    // 使用 returnType.fields 动态生成表单字段
    const fields = flattenFields(method.returnType.fields)
    for (const field of fields) {
      form.value.responseFields[field.name] = ''
    }
  }

  // 重置条件
  form.value.conditions = []
}

// 添加条件
const addCondition = () => {
  form.value.conditions.push({
    paramIndex: 0,
    operator: 'equals',
    value: ''
  })
}

// 移除条件
const removeCondition = (index: number) => {
  form.value.conditions.splice(index, 1)
}

// 条件参数变更
const onConditionParamChange = (condition: Condition) => {
  // 可以在这里根据参数类型设置默认操作符
}

// 打开创建对话框
const openCreateDialog = async () => {
  isEditing.value = false
  editingId.value = null
  form.value = {
    serviceName: route.query.service as string || '',
    methodName: '',
    mockType: 'SHORT_CIRCUIT',
    conditions: [],
    responseDelayMs: 0,
    responseBody: '{}',
    responseFields: {},
    enabled: true
  }
  useJsonMode.value = false
  dialogVisible.value = true

  if (form.value.serviceName) {
    await onServiceChange()
  }
}

// 编辑规则
const editRule = async (rule: any) => {
  isEditing.value = true
  editingId.value = rule.id

  // 解析条件规则 - 必须在调用 onServiceChange 之前解析！
  let conditions: Condition[] = []
  if (rule.conditionRule) {
    try {
      const cond = JSON.parse(rule.conditionRule)
      if (Array.isArray(cond)) {
        conditions = cond.map(c => ({
          paramIndex: c.index !== undefined ? c.index : (c.argIndex || 0),
          operator: c.operator || 'equals',
          value: c.value !== undefined ? c.value : (c.matchValue || '')
        }))
      } else if (typeof cond === 'object' && cond.argIndex !== undefined) {
        conditions = [{
          paramIndex: cond.argIndex,
          operator: cond.operator || 'equals',
          value: cond.matchValue || cond.value || ''
        }]
      }
    } catch (e) {
      console.error('解析条件规则失败:', e)
    }
  }

  // 先保存服务名和方法名，再调用 onServiceChange
  const savedServiceName = rule.serviceName
  const savedMethodName = rule.methodName

  // 解析响应体 - 支持多种格式
  let responseFields: Record<string, string> = {}
  let responseBody = rule.responseBody || '{}'
  try {
    const parsed = JSON.parse(responseBody)
    if (typeof parsed === 'object' && parsed !== null) {
      responseFields = convertToFields(parsed)
    }
  } catch (e) {
    console.error('解析响应体失败:', e)
    responseFields = {}
  }

  // 设置表单基本数据
  form.value = {
    serviceName: savedServiceName,
    methodName: '', // 先清空，让 onServiceChange 重新加载
    mockType: rule.mockType || 'SHORT_CIRCUIT',
    conditions: [], // 先清空
    responseDelayMs: rule.responseDelayMs || 0,
    responseBody: responseBody,
    responseFields: {},
    enabled: rule.enabled
  }

  // 调用 onServiceChange 加载服务元数据（会清空 conditions）
  await onServiceChange()

  // 关键修复：在 onServiceChange 之后，必须手动恢复解析好的 conditions！
  // 因为 onServiceChange 会把 conditions 清空
  form.value.conditions = conditions
  form.value.methodName = savedMethodName

  // 恢复响应字段
  form.value.responseFields = responseFields

  // 重新计算返回类型字段
  const method = availableMethods.value.find(m => m.name === savedMethodName)
  if (method && returnTypeFields.value.length > 0) {
    // 填充已保存的值
    for (const field of returnTypeFields.value) {
      if (responseFields[field.name] === undefined) {
        form.value.responseFields[field.name] = ''
      }
    }
  }

  dialogVisible.value = true
  await nextTick()
}

// 转换响应数据为表单字段格式
const convertToFields = (obj: any, prefix = ''): Record<string, string> => {
  const result: Record<string, string> = {}
  if (!obj || typeof obj !== 'object') {
    return result
  }

  for (const [key, value] of Object.entries(obj)) {
    const fullName = prefix ? `${prefix}.${key}` : key
    if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
      // 递归处理嵌套对象，但扁平化存储
      Object.assign(result, convertToFields(value, fullName))
    } else {
      result[fullName] = String(value ?? '')
    }
  }
  return result
}

// 保存规则
const saveRule = async () => {
  if (!form.value.serviceName || !form.value.methodName) {
    ElMessage.warning('请选择服务和方法')
    return
  }

  // 获取响应体
  let responseBody = '{}'
  if (useJsonMode.value) {
    responseBody = editor?.getValue() || form.value.responseBody
    try {
      JSON.parse(responseBody)
    } catch (e) {
      ElMessage.error('Mock 数据 JSON 格式错误')
      return
    }
  } else {
    // 从表单字段构建 JSON
    responseBody = JSON.stringify(form.value.responseFields)
  }

  // 构建条件规则
  let conditionRule = ''
  if (form.value.conditions.length > 0) {
    conditionRule = JSON.stringify(form.value.conditions.map(c => ({
      index: c.paramIndex,
      operator: c.operator,
      value: c.value
    })))
  }

  saving.value = true
  try {
    const payload = {
      serviceName: form.value.serviceName,
      methodName: form.value.methodName,
      mockType: form.value.mockType,
      conditionRule,
      responseType: 'success',
      responseBody,
      responseDelayMs: form.value.responseDelayMs,
      enabled: form.value.enabled
    }

    if (isEditing.value && editingId.value) {
      await axios.put(`/api/v1/rules/${editingId.value}`, {
        id: editingId.value,
        ...payload
      })
      ElMessage.success('规则更新成功')
    } else {
      await axios.post('/api/v1/rules', payload)
      ElMessage.success('规则创建成功')
    }

    dialogVisible.value = false
    await fetchRules()
  } catch (error: any) {
    console.error('保存规则失败:', error)
    ElMessage.error(error.response?.data?.message || '保存规则失败')
  } finally {
    saving.value = false
  }
}

// 切换规则状态
const toggleRule = async (rule: any) => {
  try {
    await axios.post(`/api/v1/rules/${rule.id}/toggle`)
    rule.enabled = !rule.enabled
    ElMessage.success(rule.enabled ? '规则已启用' : '规则已禁用')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 删除规则
const deleteRule = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这条规则吗？', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await axios.delete(`/api/v1/rules/${id}`)
    ElMessage.success('规则已删除')
    await fetchRules()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(async () => {
  await fetchServices()
  await fetchRules()

  // 如果从拓扑图带参数过来
  if (route.query.service) {
    form.value.serviceName = route.query.service as string
    await onServiceChange()
  }
})

watch(dialogVisible, async (val) => {
  if (val && useJsonMode.value) {
    await nextTick()
    initMonaco()
  }
})
</script>

<style scoped>
.mock-dialog :deep(.el-dialog__body) {
  max-height: 70vh;
  overflow-y: auto;
}
</style>