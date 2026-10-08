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
