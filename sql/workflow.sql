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

COMMENT ON TABLE  srm_rfq_workflow IS '询比价(RFQ)工作流';
COMMENT ON COLUMN srm_rfq_workflow.id IS '主键 ID';
COMMENT ON COLUMN srm_rfq_workflow.rfq_no IS '询价单号';
COMMENT ON COLUMN srm_rfq_workflow.material_name IS '物料名称';
COMMENT ON COLUMN srm_rfq_workflow.quantity IS '采购数量';
COMMENT ON COLUMN srm_rfq_workflow.supplier_ids IS '候选供应商 ID 列表（JSON 数组）';
COMMENT ON COLUMN srm_rfq_workflow.quotes IS '报价记录（JSON 数组）';
COMMENT ON COLUMN srm_rfq_workflow.ai_analysis IS 'AI 分析结果（JSON）';
COMMENT ON COLUMN srm_rfq_workflow.status IS '状态（QUOTING/SCORING/RECOMMENDED/CONFIRMED 等）';
COMMENT ON COLUMN srm_rfq_workflow.quote_deadline IS '报价截止时间';
COMMENT ON COLUMN srm_rfq_workflow.confirm_by IS '定标确认人 ID';
COMMENT ON COLUMN srm_rfq_workflow.confirm_time IS '定标确认时间';
COMMENT ON COLUMN srm_rfq_workflow.order_id IS '关联采购订单 ID';
COMMENT ON COLUMN srm_rfq_workflow.create_time IS '创建时间';
COMMENT ON COLUMN srm_rfq_workflow.update_time IS '更新时间';
COMMENT ON COLUMN srm_rfq_workflow.create_by IS '创建人 ID';
COMMENT ON COLUMN srm_rfq_workflow.update_by IS '更新人 ID';
COMMENT ON COLUMN srm_rfq_workflow.is_deleted IS '逻辑删除: 0-否 1-是';
