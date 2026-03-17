/**
 * 服务保护配置相关 API
 */
import axios from 'axios'
import type { ProtectionConfig, ProtectionConfigForm } from '@/types'

interface ConfigsResponse {
  configs: ProtectionConfig[]
}

export const protectionApi = {
  /**
   * 获取所有保护配置
   */
  list: async (): Promise<ProtectionConfig[]> => {
    const response = await axios.get('/api/v1/protection/configs', { timeout: 10000 })
    return response.data.configs || []
  },

  /**
   * 获取单个服务的保护配置
   */
  get: async (serviceName: string): Promise<ProtectionConfig | null> => {
    try {
      const response = await axios.get(`/api/v1/protection/configs/${encodeURIComponent(serviceName)}`, { timeout: 10000 })
      return response.data
    } catch (error: any) {
      // 404 表示配置不存在，返回 null
      if (error.response?.status === 404) {
        return null
      }
      // 其他错误打印日志并返回 null
      console.error('获取保护配置失败:', error.message || error)
      return null
    }
  },

  /**
   * 更新熔断器配置
   */
  updateCircuitBreaker: async (serviceName: string, config: {
    enabled: boolean
    threshold: number
    timeout: number
  }): Promise<void> => {
    await axios.put(`/api/v1/protection/configs/${encodeURIComponent(serviceName)}/circuit-breaker`, config, { timeout: 10000 })
  },

  /**
   * 更新限流器配置
   */
  updateRateLimiter: async (serviceName: string, config: {
    enabled: boolean
    permits: number
  }): Promise<void> => {
    await axios.put(`/api/v1/protection/configs/${encodeURIComponent(serviceName)}/rate-limiter`, config, { timeout: 10000 })
  },

  /**
   * 更新集群配置
   */
  updateCluster: async (serviceName: string, config: {
    timeoutMs: number
    retries: number
    clusterStrategy: string
  }): Promise<void> => {
    await axios.put(`/api/v1/protection/configs/${encodeURIComponent(serviceName)}/cluster`, config, { timeout: 10000 })
  },

  /**
   * 保存完整配置（组合上述操作）
   */
  saveConfig: async (form: ProtectionConfigForm): Promise<void> => {
    await protectionApi.updateCircuitBreaker(form.serviceName, {
      enabled: form.circuitBreakerEnabled,
      threshold: form.circuitBreakerThreshold,
      timeout: form.circuitBreakerTimeout
    })

    await protectionApi.updateRateLimiter(form.serviceName, {
      enabled: form.rateLimiterEnabled,
      permits: form.rateLimiterPermits
    })

    await protectionApi.updateCluster(form.serviceName, {
      timeoutMs: form.timeoutMs,
      retries: form.retries,
      clusterStrategy: form.clusterStrategy
    })
  },

  /**
   * 删除保护配置
   */
  delete: async (serviceName: string): Promise<void> => {
    await axios.delete(`/api/v1/protection/configs/${encodeURIComponent(serviceName)}`, { timeout: 10000 })
  }
}