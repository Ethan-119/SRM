-- ============================================
-- SRM 混合检索 RAG - pgvector 向量索引（只存向量，不含正文）
-- 文档正文在 rag_document 主数据表（业务库）。
-- ============================================

-- 1. 启用向量扩展（需超级用户或具备 CREATE EXTENSION 权限）
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 向量索引表：doc_id 指向 rag_document.id，embedding 存向量
CREATE TABLE IF NOT EXISTS vector_store (
    doc_id     TEXT PRIMARY KEY,
    embedding  vector(1024)
);

COMMENT ON TABLE  vector_store IS 'RAG 向量索引';
COMMENT ON COLUMN vector_store.doc_id IS '文档 ID（关联 rag_document.id）';
COMMENT ON COLUMN vector_store.embedding IS '文档向量（1024 维）';

-- 3. 余弦相似度检索索引（与代码中的 embedding <=> ?::vector 一致）
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON vector_store USING hnsw (embedding vector_cosine_ops);
