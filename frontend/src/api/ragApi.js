import http from './http'

/** 混合检索（向量 + 图谱结构化过滤） */
export function searchRag(query, topK = 5) {
  return http.get('/rag/search', { params: { query, topK } }).then((r) => r.data)
}

/** 写入/更新单条向量文档 */
export function addRagDocument(doc) {
  return http.post('/rag/documents', doc).then((r) => r.data)
}

/** 批量写入/更新向量文档 */
export function batchAddRagDocuments(docs) {
  return http.post('/rag/documents/batch', docs).then((r) => r.data)
}

/** 上传文件（PDF/Word/CSV/TXT/MD），后端解析后异步向量化 */
export function uploadRagDocuments(files) {
  const fd = new FormData()
  for (const f of files) {
    fd.append('files', f)
  }
  return http.post('/rag/documents/upload', fd).then((r) => r.data)
}

/** 数据清洗预览（不落库，返回清洗统计 + 逐行记录/清洗后文本） */
export function cleanRagPreview(formData) {
  return http.post('/rag/clean/preview', formData).then((r) => r.data)
}
