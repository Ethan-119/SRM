<script setup>
import { ref, nextTick, onMounted, computed } from 'vue'
import { sendMessageStream, fetchHistory, clearHistory } from '@/api/agentApi'

const SESSIONS_KEY = 'srm_agent_sessions'
const ACTIVE_KEY = 'srm_agent_active_session'

function loadSessions() {
  try {
    const raw = localStorage.getItem(SESSIONS_KEY)
    return raw ? JSON.parse(raw) : []
  } catch { return [] }
}
function saveSessions(list) {
  localStorage.setItem(SESSIONS_KEY, JSON.stringify(list))
}

function generateSession() {
  return {
    id: 's_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8),
    label: '新对话',
    createdAt: Date.now(),
  }
}

const sessions = ref(loadSessions())
const activeId = ref(localStorage.getItem(ACTIVE_KEY) || '')
const messages = ref([])
const input = ref('')
const sending = ref(false)
const chatBody = ref(null)

const activeSession = computed(() => sessions.value.find((s) => s.id === activeId.value))

function scrollBottom() {
  nextTick(() => {
    if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight
  })
}

async function loadHistoryFor(sessionId) {
  messages.value = []
  if (!sessionId) return
  try {
    const list = await fetchHistory(sessionId)
    messages.value = (Array.isArray(list) ? list : []).map((m) => ({
      role: m.role,
      content: m.content,
    }))
    scrollBottom()
  } catch (e) {
    console.warn('加载对话历史失败:', e.message || e)
  }
}

function switchSession(id) {
  activeId.value = id
  localStorage.setItem(ACTIVE_KEY, id)
  loadHistoryFor(id)
}

function newSession() {
  const s = generateSession()
  sessions.value.unshift(s)
  saveSessions(sessions.value)
  switchSession(s.id)
}

function deleteSession(id) {
  const idx = sessions.value.findIndex((s) => s.id === id)
  if (idx === -1) return
  sessions.value.splice(idx, 1)
  saveSessions(sessions.value)
  clearHistory(id).catch(() => {})

  if (id === activeId.value) {
    if (sessions.value.length > 0) {
      switchSession(sessions.value[0].id)
    } else {
      activeId.value = ''
      localStorage.removeItem(ACTIVE_KEY)
      messages.value = []
    }
  }
}

async function send() {
  const text = input.value.trim()
  if (!text || sending.value || !activeId.value) return

  // update session label with first message
  if (activeSession.value && activeSession.value.label === '新对话') {
    activeSession.value.label = text.slice(0, 25) + (text.length > 25 ? '…' : '')
    saveSessions(sessions.value)
  }

  messages.value.push({ role: 'user', content: text })
  messages.value.push({ role: 'assistant', content: '', streaming: true })
  input.value = ''
  sending.value = true
  scrollBottom()

  try {
    const resp = await sendMessageStream(text, activeId.value)
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    const aiMsg = messages.value[messages.value.length - 1]

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (!line.startsWith('data: ')) continue
        try {
          const json = JSON.parse(line.slice(6))
          if (json.done) break
          if (json.content) {
            aiMsg.content += json.content
            scrollBottom()
          }
        } catch { /* skip malformed */ }
      }
    }
    aiMsg.streaming = false
    // 如果流式结束后没有收到任何内容，显示提示
    if (!aiMsg.content) {
      aiMsg.content = '抱歉，AI 助手未生成回复，请稍后重试或换个方式提问。'
      aiMsg.error = true
    }
  } catch {
    const aiMsg = messages.value[messages.value.length - 1]
    aiMsg.content = aiMsg.content || '抱歉，AI 助手暂时不可用，请稍后重试。'
    aiMsg.streaming = false
    aiMsg.error = true
  } finally {
    sending.value = false
    scrollBottom()
  }
}

function renderMarkdown(text) {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>')
    .replace(/^- (.+)$/gm, '· $1')
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

onMounted(() => {
  if (activeId.value && activeSession.value) {
    loadHistoryFor(activeId.value)
  } else if (sessions.value.length > 0) {
    switchSession(sessions.value[0].id)
  }
})
</script>

<template>
  <div class="page agent-page">
    <div class="agent-layout">
      <!-- 侧边栏：会话列表 -->
      <aside class="agent-sidebar">
        <div class="sidebar-head">
          <h2>对话记录</h2>
          <button type="button" class="btn" @click="newSession">新建对话</button>
        </div>
        <div class="session-list">
          <div v-if="sessions.length === 0" class="session-empty">
            暂无对话，点击上方按钮开始
          </div>
          <div
            v-for="s in sessions"
            :key="s.id"
            class="session-item"
            :class="{ active: s.id === activeId }"
            @click="switchSession(s.id)"
          >
            <span class="session-label">{{ s.label }}</span>
            <button
              type="button"
              class="session-delete"
              title="删除对话"
              @click.stop="deleteSession(s.id)"
            >&times;</button>
          </div>
        </div>
      </aside>

      <!-- 主聊天区 -->
      <section class="panel agent-panel">
        <div class="panel-head">
          <div>
            <h1>智能采购助手</h1>
            <p class="lead">询价、比价、供应商推荐、订单咨询</p>
          </div>
        </div>

        <div ref="chatBody" class="chat-body">
          <div v-if="!activeId" class="chat-placeholder">
            <span class="placeholder-icon">&#x1F916;</span>
            <p>点击左侧「新建对话」开始使用智能助手</p>
          </div>
          <div v-else-if="messages.length === 0" class="chat-placeholder">
            <span class="placeholder-icon">&#x1F916;</span>
            <p>我是您的智能采购助手，可以帮您：</p>
            <ul>
              <li>搜索供应商（按地区、品类、资质）</li>
              <li>查询物料历史价格</li>
              <li>计算采购成本与阶梯折扣</li>
              <li>了解供应商/订单状态流转规则</li>
            </ul>
          </div>

          <div
            v-for="(msg, i) in messages"
            :key="i"
            class="chat-msg"
            :class="{ user: msg.role === 'user', error: msg.error }"
          >
            <div class="chat-msg-bubble">
              <span class="chat-msg-role">{{ msg.role === 'user' ? '你' : 'AI' }}</span>
              <div v-if="msg.role === 'assistant'" v-html="renderMarkdown(msg.content) || (msg.streaming ? '思考中…' : '')" />
              <div v-else>{{ msg.content }}</div>
            </div>
          </div>
        </div>

        <div class="chat-input-bar">
          <textarea
            v-model="input"
            placeholder="输入问题，例如：帮我找华东地区电子元器件的供应商"
            :disabled="sending || !activeId"
            rows="2"
            @keydown="onKeydown"
          />
          <button type="button" class="btn" :disabled="sending || !input.trim() || !activeId" @click="send">
            {{ sending ? '发送' : '发送' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.agent-page {
  max-width: 1200px;
}

.agent-layout {
  display: flex;
  gap: 1rem;
  height: calc(100vh - 100px);
  min-height: 560px;
}

/* ---- 侧边栏 ---- */
.agent-sidebar {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-radius: var(--radius);
  border: 1px solid var(--card-border);
  background: linear-gradient(180deg, rgba(255,255,255,0.04) 0%, rgba(15,23,42,0.5) 100%);
  box-shadow: var(--shadow);
  backdrop-filter: blur(14px);
  overflow: hidden;
}

.sidebar-head {
  padding: 0.85rem 1rem;
  border-bottom: 1px solid var(--card-border);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.sidebar-head h2 {
  margin: 0;
  font-size: 0.85rem;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.sidebar-head .btn {
  padding: 0.35rem 0.75rem;
  font-size: 0.8rem;
  width: 100%;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 0.5rem 0;
}

.session-empty {
  padding: 1rem;
  font-size: 0.8rem;
  color: var(--dim);
  text-align: center;
}

.session-item {
  display: flex;
  align-items: center;
  padding: 0.55rem 1rem;
  cursor: pointer;
  font-size: 0.82rem;
  color: var(--muted);
  border-left: 3px solid transparent;
  transition: background 0.12s, color 0.12s;
}
.session-item:hover { background: rgba(255,255,255,0.04); color: var(--text); }
.session-item.active {
  color: var(--text);
  border-left-color: var(--accent);
  background: rgba(56,189,248,0.08);
}
.session-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-delete {
  background: none;
  border: none;
  color: var(--dim);
  cursor: pointer;
  font-size: 1.1rem;
  padding: 0 0.25rem;
  opacity: 0;
  transition: opacity 0.12s, color 0.12s;
  line-height: 1;
}
.session-item:hover .session-delete { opacity: 1; }
.session-delete:hover { color: var(--danger); }

/* ---- 聊天面板 ---- */
.agent-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-bottom: 0;
  padding: 0;
  overflow: hidden;
}
.agent-panel .panel-head {
  padding: 0.85rem 1.2rem;
  margin-bottom: 0;
  border-bottom: 1px solid var(--card-border);
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 1rem 1.2rem;
}

.chat-placeholder {
  text-align: center;
  padding: 2.5rem 1rem;
  color: var(--muted);
}
.chat-placeholder .placeholder-icon { font-size: 2.5rem; display: block; margin-bottom: 0.5rem; }
.chat-placeholder ul {
  display: inline-block;
  text-align: left;
  margin-top: 0.75rem;
  line-height: 1.9;
  font-size: 0.88rem;
}

.chat-msg {
  display: flex;
  margin-bottom: 1rem;
}
.chat-msg.user { justify-content: flex-end; }

.chat-msg-bubble {
  max-width: 75%;
  padding: 0.6rem 0.85rem;
  border-radius: 10px;
  font-size: 0.9rem;
  line-height: 1.6;
}
.chat-msg-role {
  display: block;
  font-size: 0.72rem;
  font-weight: 600;
  margin-bottom: 0.2rem;
  color: var(--dim);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.chat-msg.user .chat-msg-bubble {
  background: rgba(56,189,248,0.15);
  border: 1px solid rgba(56,189,248,0.25);
  border-bottom-right-radius: 3px;
}
.chat-msg:not(.user) .chat-msg-bubble {
  background: rgba(255,255,255,0.04);
  border: 1px solid var(--card-border);
  border-bottom-left-radius: 3px;
}
.chat-msg.error .chat-msg-bubble {
  background: var(--danger-bg);
  border-color: rgba(251,113,133,0.35);
  color: #fecdd3;
}

.chat-input-bar {
  display: flex;
  gap: 0.6rem;
  padding: 0.7rem 1rem;
  border-top: 1px solid var(--card-border);
  background: rgba(0,0,0,0.15);
}
.chat-input-bar textarea {
  flex: 1;
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 10px;
  padding: 0.5rem 0.7rem;
  font-size: 0.875rem;
  resize: none;
  font-family: inherit;
  color: var(--text);
  background: rgba(2,6,23,0.55);
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.chat-input-bar textarea:focus {
  border-color: rgba(56,189,248,0.55);
  box-shadow: 0 0 0 3px rgba(56,189,248,0.12);
}
.chat-input-bar .btn { align-self: flex-end; white-space: nowrap; }

@media (max-width: 700px) {
  .agent-layout { flex-direction: column; height: auto; }
  .agent-sidebar { width: 100%; max-height: 200px; }
  .agent-panel { min-height: 420px; }
}
</style>
