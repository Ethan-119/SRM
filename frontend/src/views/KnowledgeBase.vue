<script setup>
import { ref } from 'vue'
import {
  searchRag,
  addRagDocument,
  batchAddRagDocuments,
  uploadRagDocuments,
  cleanRagPreview,
} from '@/api/ragApi'

// —— 数据清洗 ——
const cleanFileInput = ref(null)
const cleanFile = ref(null)
const cleanText = ref('')
const cleaning = ref(false)
const importing = ref(false)
const cleanMsg = ref(null)
const cleanResult = ref(null)

// —— 文件导入 ——
const fileInput = ref(null)
const uploadFiles = ref([])
const uploading = ref(false)
const uploadMsg = ref(null)

// —— 补充文档 ——
const docId = ref('')
const docContent = ref('')
const docMetadata = ref('')
const saving = ref(false)
const saveMsg = ref(null)

// —— 批量导入 ——
const batchText = ref('')
const batchSaving = ref(false)
const batchMsg = ref(null)

// —— 检索验证 ——
const query = ref('')
const topK = ref(5)
const searching = ref(false)
const searched = ref(false)
const searchError = ref('')
const results = ref([])

function onCleanFileChange(e) {
  const f = e.target.files && e.target.files[0]
  cleanFile.value = f || null
}

async function submitClean() {
  if (!cleanFile.value && !cleanText.value.trim()) {
    cleanMsg.value = { type: 'error', text: '请选择文件或粘贴文本' }
    return
  }
  cleaning.value = true
  cleanMsg.value = null
  cleanResult.value = null
  try {
    const fd = new FormData()
    if (cleanFile.value) {
      fd.append('file', cleanFile.value)
    } else {
      fd.append('text', cleanText.value)
    }
    cleanResult.value = await cleanRagPreview(fd)
    const r = cleanResult.value
    const s = r.stats || {}
    if (r.recordText) {
      cleanMsg.value = {
        type: 'ok',
        text: `识别为 ${r.recordCount} 条记录（去空行 ${s.removedEmptyLines}、去重复行 ${s.removedDuplicateLines}、去乱码字符 ${s.removedControlChars}），请检查后导入`,
      }
    } else {
      cleanMsg.value = {
        type: 'ok',
        text: `长文清洗完成（去空行 ${s.removedEmptyLines}、去重复行 ${s.removedDuplicateLines}、去乱码字符 ${s.removedControlChars}）`,
      }
    }
  } catch (e) {
    cleanMsg.value = { type: 'error', text: e.message || '清洗失败' }
  } finally {
    cleaning.value = false
  }
}

async function confirmCleanImport() {
  const r = cleanResult.value
  if (!r || !r.recordText || !r.records || r.records.length === 0) {
    cleanMsg.value = { type: 'error', text: '没有可导入的记录，请先「清洗预览」' }
    return
  }
  importing.value = true
  try {
    const docs = r.records.map((rec) => ({
      id: rec.id,
      title: rec.title,
      content: rec.content,
      source: rec.source,
      metadata: rec.metadata,
    }))
    const count = await batchAddRagDocuments(docs)
    cleanMsg.value = { type: 'ok', text: `已提交 ${count} 条记录，后台异步向量化中` }
    cleanResult.value = null
    cleanText.value = ''
    cleanFile.value = null
    if (cleanFileInput.value) cleanFileInput.value.value = ''
  } catch (e) {
    cleanMsg.value = { type: 'error', text: e.message || '导入失败' }
  } finally {
    importing.value = false
  }
}

function onFileChange(e) {
  uploadFiles.value = Array.from(e.target.files || [])
}

async function submitUpload() {
  if (uploadFiles.value.length === 0) {
    uploadMsg.value = { type: 'error', text: '请先选择要上传的文件' }
    return
  }
  uploading.value = true
  uploadMsg.value = null
  try {
    const results = await uploadRagDocuments(uploadFiles.value)
    const ok = (results || []).filter((r) => r.chunks > 0)
    const failed = (results || []).filter((r) => r.chunks === 0)
    const total = ok.reduce((s, r) => s + r.chunks, 0)
    let text = `已提交 ${total} 个文本块（${ok.length} 个文件），后台异步向量化中`
    if (failed.length) {
      text += `；${failed.length} 个文件失败`
    }
    uploadMsg.value = { type: failed.length ? 'error' : 'ok', text }
    uploadFiles.value = []
    if (fileInput.value) fileInput.value.value = ''
  } catch (e) {
    uploadMsg.value = { type: 'error', text: e.message || '上传失败' }
  } finally {
    uploading.value = false
  }
}

async function submitDocument() {
  if (!docId.value.trim() || !docContent.value.trim()) {
    saveMsg.value = { type: 'error', text: '文档 ID 和正文内容不能为空' }
    return
  }
  let metadata = null
  if (docMetadata.value.trim()) {
    try {
      metadata = JSON.parse(docMetadata.value.trim())
    } catch {
      saveMsg.value = { type: 'error', text: 'metadata 必须是合法 JSON（或留空）' }
      return
    }
  }
  saving.value = true
  saveMsg.value = null
  try {
    await addRagDocument({ id: docId.value.trim(), content: docContent.value, metadata })
    saveMsg.value = { type: 'ok', text: `已提交「${docId.value.trim()}」，后台异步向量化中` }
    docId.value = ''
    docContent.value = ''
    docMetadata.value = ''
  } catch (e) {
    saveMsg.value = { type: 'error', text: e.message || '写入失败' }
  } finally {
    saving.value = false
  }
}

async function submitBatch() {
  if (!batchText.value.trim()) {
    batchMsg.value = { type: 'error', text: '请先粘贴 JSON 数组' }
    return
  }
  let docs
  try {
    docs = JSON.parse(batchText.value.trim())
  } catch {
    batchMsg.value = { type: 'error', text: '粘贴的内容必须是合法 JSON 数组' }
    return
  }
  if (!Array.isArray(docs) || docs.length === 0) {
    batchMsg.value = { type: 'error', text: 'JSON 数组不能为空' }
    return
  }
  batchSaving.value = true
  batchMsg.value = null
  try {
    const count = await batchAddRagDocuments(docs)
    batchMsg.value = { type: 'ok', text: `已提交 ${count} 条文档，后台异步向量化中` }
    batchText.value = ''
  } catch (e) {
    batchMsg.value = { type: 'error', text: e.message || '批量写入失败' }
  } finally {
    batchSaving.value = false
  }
}

async function doSearch() {
  if (!query.value.trim()) return
  searching.value = true
  searched.value = true
  searchError.value = ''
  try {
    results.value = await searchRag(query.value.trim(), Number(topK.value) || 5)
  } catch (e) {
    searchError.value = e.message || '检索失败'
    results.value = []
  } finally {
    searching.value = false
  }
}

function formatSimilarity(v) {
  return typeof v === 'number' ? (v * 100).toFixed(1) + '%' : '-'
}
</script>

<template>
  <div class="page">
    <div class="panel-head">
      <div>
        <h1>知识库 · 向量文档</h1>
        <p class="lead">手动补充 RAG 知识库：把文档片段写入 pgvector，并验证向量检索召回效果。</p>
      </div>
    </div>

    <!-- 数据清洗 -->
    <section class="panel">
      <h2>数据清洗</h2>
      <p class="lead" style="margin: -0.4rem 0 0.9rem; font-size: 0.85rem; color: var(--muted)">
        上传或粘贴 txt / csv 原始数据，清洗并识别为「一行一档」，人工确认后再导入向量库。
      </p>
      <label class="field">
        <span>上传文件（txt / csv）</span>
        <input ref="cleanFileInput" type="file" accept=".txt,.csv,.tsv" @change="onCleanFileChange" />
      </label>
      <label class="field" style="margin-top: 0.75rem">
        <span>或粘贴原始文本</span>
        <textarea
          v-model="cleanText"
          rows="5"
          placeholder="每行一条记录，tab 或逗号分隔：id ␉ 标题 ␉ 正文 ␉ 来源 ␉ metadata"
        ></textarea>
      </label>
      <div class="toolbar" style="margin-top: 0.85rem">
        <button class="btn secondary" :disabled="cleaning" @click="submitClean">
          {{ cleaning ? '清洗中…' : '清洗预览' }}
        </button>
        <button
          class="btn"
          :disabled="importing || !cleanResult || !cleanResult.recordText"
          @click="confirmCleanImport"
        >
          {{ importing ? '导入中…' : '确认导入' }}
        </button>
      </div>
      <div v-if="cleanMsg" class="msg" :class="cleanMsg.type" style="margin-top: 0.85rem; margin-bottom: 0">
        {{ cleanMsg.text }}
      </div>

      <div
        v-if="cleanResult && cleanResult.recordText && cleanResult.records.length"
        class="table-wrap"
        style="margin-top: 0.85rem"
      >
        <table class="data">
          <thead>
            <tr>
              <th>ID</th>
              <th>标题</th>
              <th>正文</th>
              <th>来源</th>
              <th>元数据</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(rec, i) in cleanResult.records" :key="i">
              <td class="mono">{{ rec.id }}</td>
              <td>{{ rec.title }}</td>
              <td>{{ rec.content }}</td>
              <td>{{ rec.source }}</td>
              <td class="mono">{{ JSON.stringify(rec.metadata) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <label
        v-else-if="cleanResult && !cleanResult.recordText"
        class="field"
        style="margin-top: 0.85rem"
      >
        <span>清洗后文本预览</span>
        <textarea :value="cleanResult.cleanedText" rows="8" readonly></textarea>
      </label>
    </section>

    <!-- 文件导入 -->
    <section class="panel">
      <h2>文件导入</h2>
      <p class="lead" style="margin: -0.4rem 0 0.9rem; font-size: 0.85rem; color: var(--muted)">
        上传 PDF / Word(.docx) / CSV / TXT / MD 文件，后端解析正文后自动切块并向量化写入知识库。
      </p>
      <label class="field">
        <span>选择文件（可多选）</span>
        <input
          ref="fileInput"
          type="file"
          multiple
          accept=".pdf,.docx,.csv,.txt,.md"
          @change="onFileChange"
        />
      </label>
      <div v-if="uploadFiles.length" class="toolbar" style="margin-top: 0.6rem">
        <span v-for="(f, i) in uploadFiles" :key="i" class="badge badge--slate">{{ f.name }}</span>
      </div>
      <div class="toolbar" style="margin-top: 0.85rem">
        <button class="btn" :disabled="uploading" @click="submitUpload">
          {{ uploading ? '解析并向量化中…' : '上传并向量化' }}
        </button>
      </div>
      <div v-if="uploadMsg" class="msg" :class="uploadMsg.type" style="margin-top: 0.85rem; margin-bottom: 0">
        {{ uploadMsg.text }}
      </div>
    </section>

    <!-- 补充文档 -->
    <section class="panel">
      <h2>补充文档</h2>
      <label class="field">
        <span>文档 ID</span>
        <input v-model="docId" placeholder="例如 supplier-14-电子元器件" />
      </label>
      <label class="field" style="margin-top: 0.75rem">
        <span>正文内容（会被向量化）</span>
        <textarea
          v-model="docContent"
          rows="4"
          placeholder="例如：厦门亿联电子科技有限公司位于华东，主营电子元器件，供应蓝牙模组，资质一级，状态待审核。"
        ></textarea>
      </label>
      <label class="field" style="margin-top: 0.75rem">
        <span>元数据 metadata（可选，JSON 格式）</span>
        <textarea
          v-model="docMetadata"
          rows="2"
          placeholder='{"region":"华东","category":"电子元器件"}'
        ></textarea>
      </label>
      <div class="toolbar" style="margin-top: 0.85rem">
        <button class="btn" :disabled="saving" @click="submitDocument">
          {{ saving ? '写入中…' : '写入文档' }}
        </button>
      </div>
      <div v-if="saveMsg" class="msg" :class="saveMsg.type" style="margin-top: 0.85rem; margin-bottom: 0">
        {{ saveMsg.text }}
      </div>
    </section>

    <!-- 批量导入 -->
    <section class="panel">
      <h2>批量导入</h2>
      <label class="field">
        <span>粘贴 JSON 数组（每项含 id / content / 可选 metadata）</span>
        <textarea
          v-model="batchText"
          rows="8"
          placeholder='[{"id":"doc-1","content":"..."},{"id":"doc-2","content":"..."}]'
        ></textarea>
      </label>
      <div class="toolbar" style="margin-top: 0.85rem">
        <button class="btn secondary" :disabled="batchSaving" @click="submitBatch">
          {{ batchSaving ? '写入中…' : '批量写入' }}
        </button>
      </div>
      <div v-if="batchMsg" class="msg" :class="batchMsg.type" style="margin-top: 0.85rem; margin-bottom: 0">
        {{ batchMsg.text }}
      </div>
    </section>

    <!-- 检索验证 -->
    <section class="panel">
      <h2>检索验证</h2>
      <div class="search-bar">
        <div class="search-field" style="flex: 1">
          <label>查询</label>
          <input
            v-model="query"
            style="width: 100%"
            placeholder="例如：华东地区电子元器件供应商"
            @keydown.enter="doSearch"
          />
        </div>
        <div class="search-field">
          <label>Top-K</label>
          <input v-model.number="topK" type="number" min="1" max="20" style="width: 80px" />
        </div>
        <button class="btn" :disabled="searching" @click="doSearch">
          {{ searching ? '检索中…' : '检索' }}
        </button>
      </div>

      <div v-if="searchError" class="msg error">{{ searchError }}</div>

      <div v-if="searching" class="loading-block"><span class="loading-spinner"></span> 检索中…</div>

      <div v-else-if="searched && results.length === 0" class="empty-state">
        <span class="emoji">🔍</span>
        未检索到相关文档
      </div>

      <div v-else-if="results.length" class="table-wrap">
        <table class="data">
          <thead>
            <tr>
              <th>相似度</th>
              <th>文档 ID</th>
              <th>内容</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="d in results" :key="d.id">
              <td class="mono">{{ formatSimilarity(d.similarity) }}</td>
              <td class="mono">{{ d.id }}</td>
              <td>{{ d.content }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
