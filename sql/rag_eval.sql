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

COMMENT ON TABLE  srm_rag_eval_run IS 'RAG 评测批次（聚合结果）';
COMMENT ON COLUMN srm_rag_eval_run.id IS '主键 ID';
COMMENT ON COLUMN srm_rag_eval_run.run_no IS '批次号';
COMMENT ON COLUMN srm_rag_eval_run.dataset IS '数据集来源';
COMMENT ON COLUMN srm_rag_eval_run.top_k IS '评测 K 值';
COMMENT ON COLUMN srm_rag_eval_run.total_cases IS '用例总数';
COMMENT ON COLUMN srm_rag_eval_run.avg_recall IS '平均召回率 Recall@K';
COMMENT ON COLUMN srm_rag_eval_run.avg_precision IS '平均精确率 Precision@K';
COMMENT ON COLUMN srm_rag_eval_run.mrr IS '平均倒数排名 MRR';
COMMENT ON COLUMN srm_rag_eval_run.avg_ndcg IS '平均 NDCG@K';
COMMENT ON COLUMN srm_rag_eval_run.avg_accuracy IS '平均答案准确率';
COMMENT ON COLUMN srm_rag_eval_run.avg_faithfulness IS '平均忠实度';
COMMENT ON COLUMN srm_rag_eval_run.latency_p50 IS '检索延迟 P50（毫秒）';
COMMENT ON COLUMN srm_rag_eval_run.latency_p95 IS '检索延迟 P95（毫秒）';
COMMENT ON COLUMN srm_rag_eval_run.latency_p99 IS '检索延迟 P99（毫秒）';
COMMENT ON COLUMN srm_rag_eval_run.create_time IS '创建时间';
COMMENT ON COLUMN srm_rag_eval_run.update_time IS '更新时间';
COMMENT ON COLUMN srm_rag_eval_run.create_by IS '创建人 ID';
COMMENT ON COLUMN srm_rag_eval_run.update_by IS '更新人 ID';
COMMENT ON COLUMN srm_rag_eval_run.is_deleted IS '逻辑删除: 0-否 1-是';

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

COMMENT ON TABLE  srm_rag_eval_result IS 'RAG 评测明细（每条用例结果）';
COMMENT ON COLUMN srm_rag_eval_result.id IS '主键 ID';
COMMENT ON COLUMN srm_rag_eval_result.run_id IS '关联评测批次 ID';
COMMENT ON COLUMN srm_rag_eval_result.case_id IS '用例 ID';
COMMENT ON COLUMN srm_rag_eval_result.question IS '问题';
COMMENT ON COLUMN srm_rag_eval_result.retrieved_doc_ids IS '检索到的文档 ID（JSON 数组）';
COMMENT ON COLUMN srm_rag_eval_result.recall IS '召回率 Recall@K';
COMMENT ON COLUMN srm_rag_eval_result.precision IS '精确率 Precision@K';
COMMENT ON COLUMN srm_rag_eval_result.mrr IS '倒数排名 MRR';
COMMENT ON COLUMN srm_rag_eval_result.ndcg IS 'NDCG@K';
COMMENT ON COLUMN srm_rag_eval_result.accuracy IS '答案准确率';
COMMENT ON COLUMN srm_rag_eval_result.faithfulness IS '忠实度';
COMMENT ON COLUMN srm_rag_eval_result.latency_ms IS '检索延迟（毫秒）';
COMMENT ON COLUMN srm_rag_eval_result.tags IS '标签（逗号分隔）';
COMMENT ON COLUMN srm_rag_eval_result.difficulty IS '难度';
COMMENT ON COLUMN srm_rag_eval_result.create_time IS '创建时间';
COMMENT ON COLUMN srm_rag_eval_result.update_time IS '更新时间';
COMMENT ON COLUMN srm_rag_eval_result.create_by IS '创建人 ID';
COMMENT ON COLUMN srm_rag_eval_result.update_by IS '更新人 ID';
COMMENT ON COLUMN srm_rag_eval_result.is_deleted IS '逻辑删除: 0-否 1-是';
