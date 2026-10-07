-- ============================================
-- SRM 供应商管理系统 - 智能询比价工作流表（PostgreSQL）
-- 在 srm 库中执行：psql -d srm -f sql/workflow.sql
-- ============================================

DROP TABLE IF EXISTS srm_rfq_workflow;
CREATE TABLE srm_rfq_workflow (
    id              BIGINT          NOT NULL,
    rfq_no          VARCHAR(32)     NOT NULL,
    material_name   VARCHAR(128)    NOT NULL,
    quantity        NUMERIC(18,4),
    supplier_ids    TEXT,
    quotes          TEXT,
    ai_analysis     TEXT,
    status          VARCHAR(32)     DEFAULT 'QUOTING',
    quote_deadline  TIMESTAMP,
    confirm_by      BIGINT,
    confirm_time    TIMESTAMP,
    order_id        BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT now(),
    update_time     TIMESTAMP       NOT NULL DEFAULT now(),
    create_by       BIGINT,
    update_by       BIGINT,
    is_deleted      INTEGER         NOT NULL DEFAULT 0,
    CONSTRAINT pk_srm_rfq_workflow PRIMARY KEY (id),
    CONSTRAINT uk_rfq_no UNIQUE (rfq_no)
);
CREATE INDEX idx_status ON srm_rfq_workflow (status);
CREATE INDEX idx_deadline ON srm_rfq_workflow (quote_deadline);
CREATE INDEX idx_is_deleted ON srm_rfq_workflow (is_deleted);
