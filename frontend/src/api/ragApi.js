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
