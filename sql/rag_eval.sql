-- ============================================
-- SRM 供应商管理系统 - RAG 评测结果记录表（PostgreSQL）
-- 在 srm 库中执行：psql -d srm -f sql/rag_eval.sql
-- ============================================

-- 1. 评测批次表（一次批量评测的聚合结果）
DROP TABLE IF EXISTS srm_rag_eval_run;
CREATE TABLE srm_rag_eval_run (
    id               BIGINT        NOT NULL,
    run_no           VARCHAR(64)   NOT NULL,
    dataset          VARCHAR(255),
    top_k            INTEGER,
    total_cases      INTEGER,
    avg_recall       NUMERIC(10,6),
    avg_precision    NUMERIC(10,6),
    mrr              NUMERIC(10,6),
    avg_ndcg         NUMERIC(10,6),
    avg_accuracy     NUMERIC(10,6),
    avg_faithfulness NUMERIC(10,6),
    latency_p50      BIGINT,
    latency_p95      BIGINT,
    latency_p99      BIGINT,
    create_time      TIMESTAMP     NOT NULL DEFAULT now(),
    update_time      TIMESTAMP     NOT NULL DEFAULT now(),
    create_by        BIGINT,
    update_by        BIGINT,
    is_deleted       INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT pk_srm_rag_eval_run PRIMARY KEY (id),
    CONSTRAINT uk_rag_eval_run_no UNIQUE (run_no)
);

-- 2. 评测明细表（每条用例的结果）
DROP TABLE IF EXISTS srm_rag_eval_result;
CREATE TABLE srm_rag_eval_result (
    id                BIGINT        NOT NULL,
    run_id            BIGINT        NOT NULL,
    case_id           VARCHAR(64)   NOT NULL,
    question          TEXT,
    retrieved_doc_ids TEXT,
    recall            NUMERIC(10,6),
    precision         NUMERIC(10,6),
    mrr               NUMERIC(10,6),
    ndcg              NUMERIC(10,6),
    accuracy          NUMERIC(10,6),
    faithfulness      NUMERIC(10,6),
    latency_ms        BIGINT,
    tags              VARCHAR(255),
    difficulty        VARCHAR(16),
    create_time       TIMESTAMP     NOT NULL DEFAULT now(),
    update_time       TIMESTAMP     NOT NULL DEFAULT now(),
    create_by         BIGINT,
    update_by         BIGINT,
    is_deleted        INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT pk_srm_rag_eval_result PRIMARY KEY (id)
);
CREATE INDEX idx_rag_eval_result_run ON srm_rag_eval_result (run_id);
