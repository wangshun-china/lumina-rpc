import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './style.css'

import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import axios from 'axios'

const app = createApp(App)

// 全局注册 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 配置 axios
// 前端代码中的请求路径已经包含 /api 前缀，所以不需要再设置 baseURL
// axios.defaults.baseURL = '/api'  -- 已移除，避免 /api/api 重复
axios.defaults.timeout = 30000
axios.defaults.headers.common['Accept'] = 'application/json'

// 请求拦截
axios.interceptors.request.use(
  (config) => {
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截
axios.interceptors.response.use(
  (response) => {
    return response
  },
  (error) => {
    console.error('HTTP Error:', error)
    return Promise.reject(error)
  }
)

app.use(router)
app.use(ElementPlus)

app.mount('#app')
