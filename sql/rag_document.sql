-- ============================================
-- RAG 文档主数据表（业务库，与 srm_supplier 等同库）
-- ============================================
CREATE TABLE IF NOT EXISTS rag_document (
    id          VARCHAR(128) PRIMARY KEY,
    title       VARCHAR(255),
    content     TEXT NOT NULL,
    source      VARCHAR(128),
    metadata    TEXT,
    create_time TIMESTAMP DEFAULT now(),
    update_time TIMESTAMP DEFAULT now()
);

COMMENT ON TABLE  rag_document IS 'RAG 文档主数据（知识库）';
COMMENT ON COLUMN rag_document.id IS '文档唯一标识';
COMMENT ON COLUMN rag_document.title IS '文档标题';
COMMENT ON COLUMN rag_document.content IS '文档正文（用于向量化）';
COMMENT ON COLUMN rag_document.source IS '来源';
COMMENT ON COLUMN rag_document.metadata IS '元数据（JSON 字符串）';
COMMENT ON COLUMN rag_document.create_time IS '创建时间';
COMMENT ON COLUMN rag_document.update_time IS '更新时间';
