import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

// 懒加载组件 - 按需加载，提升首屏和切换速度
const Dashboard = () => import('../views/Dashboard.vue')
const TopologyView = () => import('../views/TopologyView.vue')
const ServicesView = () => import('../views/ServicesView.vue')
const MockRulesView = () => import('../views/MockRulesView.vue')
const ConsumerOpsView = () => import('../views/ConsumerOpsView.vue')
const ProtectionConfigView = () => import('../views/ProtectionConfigView.vue')
const TraceView = () => import('../views/TraceView.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'dashboard',
    component: Dashboard,
    meta: {
      title: 'Lumina-RPC - Dashboard',
    },
  },
  {
    path: '/topology',
    name: 'topology',
    component: TopologyView,
    meta: {
      title: 'Lumina-RPC - 拓扑视图',
    },
  },
  {
    path: '/services',
    name: 'services',
    component: ServicesView,
    meta: {
      title: 'Lumina-RPC - 服务管理',
    },
  },
  {
    path: '/mock-rules',
    name: 'mock-rules',
    component: MockRulesView,
    meta: {
      title: 'Lumina-RPC - Mock 规则',
    },
  },
  {
    path: '/consumer-ops',
    name: 'consumer-ops',
    component: ConsumerOpsView,
    meta: {
      title: 'Lumina-RPC - 消费者操作台',
    },
  },
  {
    path: '/protection',
    name: 'protection',
    component: ProtectionConfigView,
    meta: {
      title: 'Lumina-RPC - 服务保护配置',
    },
  },
  {
    path: '/traces',
    name: 'traces',
    component: TraceView,
    meta: {
      title: 'Lumina-RPC - 链路追踪',
    },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach((to, _from, next) => {
  if (to.meta.title) {
    document.title = to.meta.title as string
  }
  next()
})

export default router