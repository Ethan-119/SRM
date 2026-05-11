import http from './http'

/**
 * 与后端约定：POST /api/auth/login
 * Body: { username, password }
 * Result.data 示例：{ token } 或 { accessToken } 或 { access_token }
 * 若你的 Controller 路径不同，只改下方 URL 即可。
 */
export async function login(username, password) {
  const result = await http.post('/auth/login', {
    username: username.trim(),
    password,
  })
  const raw = result.data
  if (!raw || typeof raw !== 'object') {
    throw new Error('登录响应无效')
  }
  const token =
    raw.token ?? raw.accessToken ?? raw.access_token ?? raw.jwt ?? raw.bearerToken
  if (!token) {
    throw new Error('登录响应缺少令牌，请检查后端返回字段')
  }
  const name =
    raw.username ?? raw.userName ?? raw.name ?? raw.loginName ?? username.trim()
  return { token: String(token), username: String(name) }
}
