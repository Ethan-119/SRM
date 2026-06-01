import axios from 'axios'
import { clearAuth, getToken } from '@/auth/session'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

function friendlyNetworkError(err) {
  const code = err.code
  const msg = String(err.message || '')
  const full = String(err)
  if (
    code === 'ECONNREFUSED' ||
    code === 'ERR_NETWORK' ||
    code === 'ETIMEDOUT' ||
    msg.includes('ECONNREFUSED') ||
    full.includes('ECONNREFUSED') ||
    msg.includes('Network Error') ||
    (!err.response && msg.includes('AggregateError'))
  ) {
    return new Error(
      '无法连接后端服务。请先在本机启动 Spring Boot（默认端口 8083），确认地址为 http://localhost:8083 后再刷新页面。'
    )
  }
  return null
}

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body.code === 'number' && body.code !== 200) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (err) => {
    const net = friendlyNetworkError(err)
    if (net) return Promise.reject(net)
    if (err.response?.status === 401) {
      clearAuth()
      const path = window.location.pathname || ''
      if (!path.endsWith('/login')) {
        window.location.href = '/login'
      }
    }
    const msg =
      err.response?.data?.message ||
      err.message ||
      '网络错误'
    return Promise.reject(new Error(msg))
  }
)

export default http
