import http from './http'
import { getToken } from '@/auth/session'

/**
 * 非流式对话
 */
export function sendMessage(query, sessionId = 'default') {
  return http.post('/agent/chat', { query, session_id: sessionId })
}

/**
 * 流式对话（SSE），返回 fetch Response，外部读取 ReadableStream
 */
export function sendMessageStream(query, sessionId = 'default') {
  const token = getToken() || ''
  return fetch('/api/agent/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: token ? `Bearer ${token}` : '',
    },
    body: JSON.stringify({ query, session_id: sessionId }),
  })
}

/**
 * 获取对话历史
 */
export function fetchHistory(sessionId) {
  return http.get(`/agent/history/${sessionId}`).then((r) => r.data)
}

/**
 * 清空对话历史
 */
export function clearHistory(sessionId) {
  return http.delete(`/agent/history/${sessionId}`).then((r) => r.data)
}
