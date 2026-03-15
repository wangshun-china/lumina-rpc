<template>
  <header class="bg-slate-900/90 backdrop-blur-md border-b border-slate-700 sticky top-0 z-50">
    <div class="container mx-auto px-4 py-4">
      <div class="flex items-center justify-between">
        <div class="flex items-center space-x-3">
          <div class="w-10 h-10 bg-gradient-to-br from-cyan-500 to-blue-600 rounded-lg flex items-center justify-center shadow-lg">
            <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
          </div>
          <div>
            <h1 class="text-xl font-bold text-white">Lumina-RPC</h1>
            <p class="text-xs text-slate-400">面向可观测性的轻量 RPC 框架</p>
          </div>
        </div>

        <nav class="hidden md:flex items-center space-x-1">
          <RouterLink
            to="/"
            class="px-4 py-2 rounded-lg text-sm font-medium transition-colors hover:bg-slate-800"
            :class="route.path === '/' ? 'bg-slate-800 text-cyan-400' : 'text-slate-400'"
          >
            大盘看板
          </RouterLink>
          <RouterLink
            to="/services"
            class="px-4 py-2 rounded-lg text-sm font-medium transition-colors hover:bg-slate-800"
            :class="route.path === '/services' ? 'bg-slate-800 text-cyan-400' : 'text-slate-400'"
          >
            服务管理
          </RouterLink>
          <RouterLink
            to="/mock-rules"
            class="px-4 py-2 rounded-lg text-sm font-medium transition-colors hover:bg-slate-800"
            :class="route.path === '/mock-rules' ? 'bg-slate-800 text-cyan-400' : 'text-slate-400'"
          >
            Mock 规则
          </RouterLink>
          <RouterLink
            to="/protection"
            class="px-4 py-2 rounded-lg text-sm font-medium transition-colors hover:bg-slate-800"
            :class="route.path === '/protection' ? 'bg-slate-800 text-cyan-400' : 'text-slate-400'"
          >
            服务保护
          </RouterLink>
          <RouterLink
            to="/consumer-ops"
            class="px-4 py-2 rounded-lg text-sm font-medium transition-colors hover:bg-slate-800 flex items-center space-x-1"
            :class="route.path === '/consumer-ops' ? 'bg-slate-800 text-cyan-400' : 'text-slate-400'"
          >
            <span>📊</span>
            <span>消费者操作台</span>
          </RouterLink>
          <RouterLink
            to="/traces"
            class="px-4 py-2 rounded-lg text-sm font-medium transition-colors hover:bg-slate-800 flex items-center space-x-1"
            :class="route.path === '/traces' ? 'bg-slate-800 text-cyan-400' : 'text-slate-400'"
          >
            <span>🔗</span>
            <span>链路追踪</span>
          </RouterLink>
        </nav>

        <div class="flex items-center space-x-4">
          <div class="text-xs text-slate-500 hidden sm:block">
            <span class="flex items-center space-x-2">
              <span class="w-2 h-2 bg-emerald-500 rounded-full animate-pulse"></span>
              <span>Control Plane 运行中</span>
            </span>
          </div>

          <div class="md:hidden">
            <button class="p-2 text-slate-400 hover:text-white transition-colors">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { RouterLink } from 'vue-router'

const route = useRoute()
</script>

<style scoped>
header {
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06);
}

nav a {
  position: relative;
}

nav a::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 0;
  height: 2px;
  background: linear-gradient(90deg, #06b6d4, #3b82f6);
  transition: width 0.3s ease, left 0.3s ease;
}

nav a:hover::after {
  width: 100%;
  left: 0;
}
</style>
