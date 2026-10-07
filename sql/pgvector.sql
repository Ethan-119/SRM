-- ============================================
-- SRM 混合检索 RAG - pgvector 向量库初始化脚本（PostgreSQL）
-- 在 srm_vector 库中执行（与业务库 srm 分开）：
--   createdb srm_vector
--   psql -d srm_vector -f sql/pgvector.sql
-- ============================================

-- 1. 启用向量扩展（需超级用户或具备 CREATE EXTENSION 权限）
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 向量存储表
CREATE TABLE IF NOT EXISTS vector_store (
    id        TEXT PRIMARY KEY,
    content   TEXT NOT NULL,
    metadata  JSONB DEFAULT '{}'::jsonb,
    embedding vector(1024)
);

-- 3. 余弦相似度检索索引（与代码中的 embedding <=> ?::vector 一致）
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON vector_store USING hnsw (embedding vector_cosine_ops);
