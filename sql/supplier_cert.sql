-- ============================================
-- 供应商资质证书表（PG 权威数据源）
-- 替代 Neo4j seed.cypher 中手工 MERGE 的 Cert 节点，
-- 资质续期先写本表，再由 Neo4jSyncService.syncCert() 同步到图谱。
-- ============================================
CREATE TABLE IF NOT EXISTS srm_supplier_cert (
    id           BIGINT       NOT NULL,
    cert_no      VARCHAR(64)  NOT NULL,
    supplier_id  BIGINT       NOT NULL,
    cert_type    VARCHAR(64),
    issue_date   DATE,
    expire_date  DATE,
    status       VARCHAR(16)  NOT NULL DEFAULT '有效',
    create_time  TIMESTAMP    NOT NULL DEFAULT now(),
    update_time  TIMESTAMP    NOT NULL DEFAULT now(),
    create_by    BIGINT,
    update_by    BIGINT,
    is_deleted   INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_srm_supplier_cert PRIMARY KEY (id),
    CONSTRAINT uk_supplier_cert_no UNIQUE (cert_no)
);

CREATE INDEX IF NOT EXISTS idx_supplier_cert_supplier ON srm_supplier_cert (supplier_id);

COMMENT ON TABLE  srm_supplier_cert IS '供应商资质证书';
COMMENT ON COLUMN srm_supplier_cert.id IS '主键 ID';
COMMENT ON COLUMN srm_supplier_cert.cert_no IS '证书编号（唯一）';
COMMENT ON COLUMN srm_supplier_cert.supplier_id IS '供应商 ID（关联 srm_supplier.id）';
COMMENT ON COLUMN srm_supplier_cert.cert_type IS '证书类型（如 ISO9001）';
COMMENT ON COLUMN srm_supplier_cert.issue_date IS '签发日期';
COMMENT ON COLUMN srm_supplier_cert.expire_date IS '过期日期';
COMMENT ON COLUMN srm_supplier_cert.status IS '状态: 有效 / 过期';
COMMENT ON COLUMN srm_supplier_cert.create_time IS '创建时间';
COMMENT ON COLUMN srm_supplier_cert.update_time IS '更新时间';
COMMENT ON COLUMN srm_supplier_cert.create_by IS '创建人 ID';
COMMENT ON COLUMN srm_supplier_cert.update_by IS '更新人 ID';
COMMENT ON COLUMN srm_supplier_cert.is_deleted IS '逻辑删除: 0-否 1-是';

-- ============================================
-- 初始数据：从 graph/seed.cypher 的 Cert 节点迁移到 PG，
-- 保证 PG 成为完整权威源后，syncCert() 全量重建不丢数据。
-- ============================================
INSERT INTO srm_supplier_cert (id, cert_no, supplier_id, cert_type, issue_date, expire_date, status)
VALUES
    (1, 'ISO9001-A', 2,  'ISO9001', '2023-01-01', '2026-10-20', '有效'),
    (2, 'ISO9001-C', 15, 'ISO9001', '2022-06-01', '2025-06-30', '过期')
ON CONFLICT (cert_no) DO NOTHING;
