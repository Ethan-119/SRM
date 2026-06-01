<script setup>
import { computed, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { clearAuth, getUsername } from '@/auth/session'
import { logout as logoutApi } from '@/api/authApi'

const route = useRoute()
const router = useRouter()

const showShell = computed(() => route.meta.requiresAuth === true)

const displayName = ref('')
watch(
  () => route.fullPath,
  () => {
    displayName.value = getUsername() || ''
  },
  { immediate: true }
)

async function logout() {
  try { await logoutApi() } catch (_) { /* 即使服务端调用失败也清除本地 */ }
  clearAuth()
  router.push('/login')
}
</script>

<template>
  <div class="app-root">
    <header v-if="showShell" class="app-nav">
      <div class="app-nav-inner">
        <span class="app-nav-brand">SRM · 管理端</span>
        <nav class="app-nav-links">
          <RouterLink to="/supplier">供应商</RouterLink>
          <RouterLink to="/orders">采购订单</RouterLink>
          <RouterLink to="/agent">智能助手</RouterLink>
        </nav>
        <div class="app-nav-user">
          <span v-if="displayName" class="nav-user-name">{{ displayName }}</span>
          <button type="button" class="btn secondary btn-nav" @click="logout">
            退出登录
          </button>
        </div>
      </div>
    </header>
    <RouterView />
  </div>
</template>
