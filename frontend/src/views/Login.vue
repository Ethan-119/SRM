<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login as loginApi } from '@/api/authApi'
import { setAuth } from '@/auth/session'

const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function onSubmit() {
  error.value = ''
  if (!username.value.trim()) {
    error.value = '请输入账号'
    return
  }
  if (!password.value) {
    error.value = '请输入密码'
    return
  }
  loading.value = true
  try {
    const { token, username: name } = await loginApi(
      username.value,
      password.value
    )
    setAuth(token, name)
    const redir = route.query.redirect
    const safe =
      typeof redir === 'string' &&
      redir.startsWith('/') &&
      !redir.startsWith('//')
        ? redir
        : '/supplier'
    router.replace(safe)
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <span class="login-logo">SRM</span>
      </div>
      <h1 class="login-title">登录</h1>
      <p class="login-sub">内部采购系统 </p>

      <form class="login-form" @submit.prevent="onSubmit">
        <label class="field login-field">
          账号
          <input
            v-model.trim="username"
            type="text"
            name="username"
            autocomplete="username"
            placeholder="用户名"
          />
        </label>
        <label class="field login-field">
          密码
          <input
            v-model="password"
            type="password"
            name="password"
            autocomplete="current-password"
            placeholder="密码"
          />
        </label>

        <div v-if="error" class="msg error">{{ error }}</div>

        <button type="submit" class="btn login-submit" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
        </button>
      </form>
    </div>
  </div>
</template>
