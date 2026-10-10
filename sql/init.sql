-- ============================================
-- SRM 供应商管理系统 - 数据库初始化脚本（PostgreSQL + pgvector）
-- 执行前先创建数据库：CREATE DATABASE "SRM";
-- 然后连接该库执行：psql -d SRM -f sql/init.sql
-- 表结构 + 向量库初始化 + 数据字典 + 管理员账号
-- 测试数据见 sql/testdata.sql
-- ============================================

-- ============================================
-- 0. 向量扩展（pgvector，需超级用户或 CREATE EXTENSION 权限）
-- ============================================
CREATE EXTENSION IF NOT EXISTS vector;


-- ============================================
-- 1. 供应商表
-- ============================================
DROP TABLE IF EXISTS srm_supplier;
CREATE TABLE srm_supplier (
    id                  BIGINT          NOT NULL,
    supplier_code       VARCHAR(32)     NOT NULL,
    supplier_name       VARCHAR(128)    NOT NULL,
    contact_person      VARCHAR(64),
    contact_phone       VARCHAR(20),
    email               VARCHAR(64),
    region              VARCHAR(64),
    main_category       VARCHAR(64),
    qualification_level INTEGER         DEFAULT 1,
    status              INTEGER         DEFAULT 0,
    address             VARCHAR(255),
    remark              VARCHAR(512),
    create_time         TIMESTAMP       NOT NULL DEFAULT now(),
    update_time         TIMESTAMP       NOT NULL DEFAULT now(),
    create_by           BIGINT,
    update_by           BIGINT,
    is_deleted          INTEGER         NOT NULL DEFAULT 0,
    CONSTRAINT pk_srm_supplier PRIMARY KEY (id),
    CONSTRAINT uk_supplier_code UNIQUE (supplier_code)
);
CREATE INDEX idx_supplier_name ON srm_supplier (supplier_name);
CREATE INDEX idx_supplier_region ON srm_supplier (region);
CREATE INDEX idx_supplier_status ON srm_supplier (status);
CREATE INDEX idx_supplier_category ON srm_supplier (main_category);
CREATE INDEX idx_supplier_is_deleted ON srm_supplier (is_deleted);

COMMENT ON TABLE  srm_supplier IS '供应商';
COMMENT ON COLUMN srm_supplier.id IS '主键 ID';
COMMENT ON COLUMN srm_supplier.supplier_code IS '供应商编码';
COMMENT ON COLUMN srm_supplier.supplier_name IS '供应商名称';
COMMENT ON COLUMN srm_supplier.contact_person IS '联系人';
COMMENT ON COLUMN srm_supplier.contact_phone IS '联系电话';
COMMENT ON COLUMN srm_supplier.email IS '邮箱';
COMMENT ON COLUMN srm_supplier.region IS '所属地区';
COMMENT ON COLUMN srm_supplier.main_category IS '主营品类';
COMMENT ON COLUMN srm_supplier.qualification_level IS '资质等级: 1-初级 2-中级 3-高级';
COMMENT ON COLUMN srm_supplier.status IS '状态: 0-注册 1-待审核 2-已准入 3-合作中 4-冻结 5-黑名单';
COMMENT ON COLUMN srm_supplier.address IS '地址';
COMMENT ON COLUMN srm_supplier.remark IS '备注';
COMMENT ON COLUMN srm_supplier.create_time IS '创建时间';
COMMENT ON COLUMN srm_supplier.update_time IS '更新时间';
COMMENT ON COLUMN srm_supplier.create_by IS '创建人 ID';
COMMENT ON COLUMN srm_supplier.update_by IS '更新人 ID';
COMMENT ON COLUMN srm_supplier.is_deleted IS '逻辑删除: 0-否 1-是';


-- ============================================
-- 2. 采购订单表
-- ============================================
DROP TABLE IF EXISTS srm_purchase_order;
CREATE TABLE srm_purchase_order (
    id              BIGINT          NOT NULL,
    order_no        VARCHAR(32)     NOT NULL,
    supplier_id     BIGINT          NOT NULL,
    material_name   VARCHAR(128)    NOT NULL,
    quantity        INTEGER         NOT NULL,
    unit_price      NUMERIC(12, 2)  NOT NULL,
    total_amount    NUMERIC(14, 2)  NOT NULL,
    delivery_date   DATE,
    status          INTEGER         DEFAULT 0,
    received_time   TIMESTAMP,
    remark          VARCHAR(512),
    create_time     TIMESTAMP       NOT NULL DEFAULT now(),
    update_time     TIMESTAMP       NOT NULL DEFAULT now(),
    create_by       BIGINT,
    update_by       BIGINT,
    is_deleted      INTEGER         NOT NULL DEFAULT 0,
    CONSTRAINT pk_srm_purchase_order PRIMARY KEY (id),
    CONSTRAINT uk_order_no UNIQUE (order_no)
);
CREATE INDEX idx_order_supplier ON srm_purchase_order (supplier_id);
CREATE INDEX idx_order_status ON srm_purchase_order (status);
CREATE INDEX idx_order_delivery ON srm_purchase_order (delivery_date);
CREATE INDEX idx_order_is_deleted ON srm_purchase_order (is_deleted);

COMMENT ON TABLE  srm_purchase_order IS '采购订单';
COMMENT ON COLUMN srm_purchase_order.id IS '主键 ID';
COMMENT ON COLUMN srm_purchase_order.order_no IS '订单号';
COMMENT ON COLUMN srm_purchase_order.supplier_id IS '供应商 ID';
COMMENT ON COLUMN srm_purchase_order.material_name IS '物料名称';
COMMENT ON COLUMN srm_purchase_order.quantity IS '数量';
COMMENT ON COLUMN srm_purchase_order.unit_price IS '单价';
COMMENT ON COLUMN srm_purchase_order.total_amount IS '总金额';
COMMENT ON COLUMN srm_purchase_order.delivery_date IS '交货日期';
COMMENT ON COLUMN srm_purchase_order.status IS '状态: 0-待确认 1-生产中 2-已发货 3-已签收 4-已取消';
COMMENT ON COLUMN srm_purchase_order.received_time IS '签收时间';
COMMENT ON COLUMN srm_purchase_order.remark IS '备注';
COMMENT ON COLUMN srm_purchase_order.create_time IS '创建时间';
COMMENT ON COLUMN srm_purchase_order.update_time IS '更新时间';
COMMENT ON COLUMN srm_purchase_order.create_by IS '创建人 ID';
COMMENT ON COLUMN srm_purchase_order.update_by IS '更新人 ID';
COMMENT ON COLUMN srm_purchase_order.is_deleted IS '逻辑删除: 0-否 1-是';


-- ============================================
-- 3. 字典项表
-- ============================================
DROP TABLE IF EXISTS srm_dict_item;
CREATE TABLE srm_dict_item (
    id          BIGINT          NOT NULL,
    dict_type   VARCHAR(64)     NOT NULL,
    label       VARCHAR(128)    NOT NULL,
    value       VARCHAR(128)    NOT NULL,
    sort        INTEGER         DEFAULT 0,
    status      INTEGER         DEFAULT 1,
    remark      VARCHAR(512),
    create_time TIMESTAMP       NOT NULL DEFAULT now(),
    update_time TIMESTAMP       NOT NULL DEFAULT now(),
    create_by   BIGINT,
    update_by   BIGINT,
    is_deleted  INTEGER         NOT NULL DEFAULT 0,
    CONSTRAINT pk_srm_dict_item PRIMARY KEY (id)
);
CREATE INDEX idx_dict_type ON srm_dict_item (dict_type);
CREATE INDEX idx_dict_is_deleted ON srm_dict_item (is_deleted);

COMMENT ON TABLE  srm_dict_item IS '字典项';
COMMENT ON COLUMN srm_dict_item.id IS '主键 ID';
COMMENT ON COLUMN srm_dict_item.dict_type IS '字典类型';
COMMENT ON COLUMN srm_dict_item.label IS '显示标签';
COMMENT ON COLUMN srm_dict_item.value IS '值';
COMMENT ON COLUMN srm_dict_item.sort IS '排序';
COMMENT ON COLUMN srm_dict_item.status IS '状态: 0-禁用 1-启用';
COMMENT ON COLUMN srm_dict_item.remark IS '备注';
COMMENT ON COLUMN srm_dict_item.create_time IS '创建时间';
COMMENT ON COLUMN srm_dict_item.update_time IS '更新时间';
COMMENT ON COLUMN srm_dict_item.create_by IS '创建人 ID';
COMMENT ON COLUMN srm_dict_item.update_by IS '更新人 ID';
COMMENT ON COLUMN srm_dict_item.is_deleted IS '逻辑删除: 0-否 1-是';


-- ============================================
-- 4. 管理端用户表
-- ============================================
DROP TABLE IF EXISTS srm_user;
CREATE TABLE srm_user (
    id          BIGINT          NOT NULL,
    username    VARCHAR(64)     NOT NULL,
    password    VARCHAR(255)    NOT NULL,
    real_name   VARCHAR(64)     NOT NULL,
    email       VARCHAR(64),
    phone       VARCHAR(20),
    department  VARCHAR(64),
    is_admin    INTEGER         NOT NULL DEFAULT 0,
    status      INTEGER         NOT NULL DEFAULT 1,
    create_time TIMESTAMP       NOT NULL DEFAULT now(),
    update_time TIMESTAMP       NOT NULL DEFAULT now(),
    create_by   BIGINT,
    update_by   BIGINT,
    is_deleted  INTEGER         NOT NULL DEFAULT 0,
    CONSTRAINT pk_srm_user PRIMARY KEY (id),
    CONSTRAINT uk_username UNIQUE (username)
);
CREATE INDEX idx_user_is_admin ON srm_user (is_admin);
CREATE INDEX idx_user_is_deleted ON srm_user (is_deleted);

COMMENT ON TABLE  srm_user IS '管理端用户（内部采购人员）';
COMMENT ON COLUMN srm_user.id IS '主键 ID';
COMMENT ON COLUMN srm_user.username IS '用户名（登录账号）';
COMMENT ON COLUMN srm_user.password IS '密码（BCrypt 加密）';
COMMENT ON COLUMN srm_user.real_name IS '真实姓名';
COMMENT ON COLUMN srm_user.email IS '邮箱';
COMMENT ON COLUMN srm_user.phone IS '联系电话';
COMMENT ON COLUMN srm_user.department IS '所属部门';
COMMENT ON COLUMN srm_user.is_admin IS '是否管理员: 0-普通员工 1-管理员';
COMMENT ON COLUMN srm_user.status IS '状态: 0-禁用 1-启用';
COMMENT ON COLUMN srm_user.create_time IS '创建时间';
COMMENT ON COLUMN srm_user.update_time IS '更新时间';
COMMENT ON COLUMN srm_user.create_by IS '创建人 ID';
COMMENT ON COLUMN srm_user.update_by IS '更新人 ID';
COMMENT ON COLUMN srm_user.is_deleted IS '逻辑删除: 0-否 1-是';


-- ============================================
-- 5. AI 会话历史表（长期记忆，配合 Redis 短期窗口）
-- ============================================
DROP TABLE IF EXISTS srm_agent_message;
CREATE TABLE srm_agent_message (
    id          BIGINT          NOT NULL,
    session_id  VARCHAR(64)     NOT NULL,
    role        VARCHAR(16)     NOT NULL,
    content     TEXT            NOT NULL,
    create_time TIMESTAMP       NOT NULL DEFAULT now(),
    CONSTRAINT pk_srm_agent_message PRIMARY KEY (id)
);
CREATE INDEX idx_agent_msg_session ON srm_agent_message (session_id, id);

COMMENT ON TABLE  srm_agent_message IS 'AI 会话历史消息（长期记忆）';
COMMENT ON COLUMN srm_agent_message.id IS '主键 ID';
COMMENT ON COLUMN srm_agent_message.session_id IS '会话 ID';
COMMENT ON COLUMN srm_agent_message.role IS '角色: user / assistant';
COMMENT ON COLUMN srm_agent_message.content IS '消息内容';
COMMENT ON COLUMN srm_agent_message.create_time IS '创建时间';


-- ============================================
-- 6. 供应商资质证书表（PG 权威数据源）
-- ============================================
DROP TABLE IF EXISTS srm_supplier_cert;
CREATE TABLE srm_supplier_cert (
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
CREATE INDEX idx_supplier_cert_supplier ON srm_supplier_cert (supplier_id);

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
-- 7. 询比价(RFQ)工作流表
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
CREATE INDEX idx_rfq_status ON srm_rfq_workflow (status);
CREATE INDEX idx_rfq_deadline ON srm_rfq_workflow (quote_deadline);
CREATE INDEX idx_rfq_is_deleted ON srm_rfq_workflow (is_deleted);

COMMENT ON TABLE  srm_rfq_workflow IS '询比价(RFQ)工作流';
COMMENT ON COLUMN srm_rfq_workflow.id IS '主键 ID';
COMMENT ON COLUMN srm_rfq_workflow.rfq_no IS '询价单号';
COMMENT ON COLUMN srm_rfq_workflow.material_name IS '物料名称';
COMMENT ON COLUMN srm_rfq_workflow.quantity IS '采购数量';
COMMENT ON COLUMN srm_rfq_workflow.supplier_ids IS '候选供应商 ID 列表（JSON 数组）';
COMMENT ON COLUMN srm_rfq_workflow.quotes IS '报价记录（JSON 数组）';
COMMENT ON COLUMN srm_rfq_workflow.ai_analysis IS 'AI 分析结果（JSON）';
COMMENT ON COLUMN srm_rfq_workflow.status IS '状态（QUOTING/ANALYZING/PENDING_CONFIRM/ORDERED/REJECTED/EXPIRED）';
COMMENT ON COLUMN srm_rfq_workflow.quote_deadline IS '报价截止时间';
COMMENT ON COLUMN srm_rfq_workflow.confirm_by IS '定标确认人 ID';
COMMENT ON COLUMN srm_rfq_workflow.confirm_time IS '定标确认时间';
COMMENT ON COLUMN srm_rfq_workflow.order_id IS '关联采购订单 ID';
COMMENT ON COLUMN srm_rfq_workflow.create_time IS '创建时间';
COMMENT ON COLUMN srm_rfq_workflow.update_time IS '更新时间';
COMMENT ON COLUMN srm_rfq_workflow.create_by IS '创建人 ID';
COMMENT ON COLUMN srm_rfq_workflow.update_by IS '更新人 ID';
COMMENT ON COLUMN srm_rfq_workflow.is_deleted IS '逻辑删除: 0-否 1-是';


-- ============================================
-- 8. RAG 文档主数据表（知识库）
-- ============================================
DROP TABLE IF EXISTS rag_document;
CREATE TABLE rag_document (
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


-- ============================================
-- 9. RAG 评测批次表（聚合结果）
-- ============================================
DROP TABLE IF EXISTS srm_rag_eval_run;
CREATE TABLE srm_rag_eval_run (
    id               BIGINT        NOT NULL,
    run_no           VARCHAR(64)   NOT NULL,
    dataset          VARCHAR(255),
    top_k            INTEGER,
    status           VARCHAR(32)   DEFAULT 'PENDING',
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
COMMENT ON COLUMN srm_rag_eval_run.status IS '状态: PENDING-排队中 RUNNING-执行中 COMPLETED-已完成 FAILED-失败';
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


-- ============================================
-- 10. RAG 评测明细表（每条用例结果）
-- ============================================
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


-- ============================================
-- 11. 向量索引表（pgvector，只存向量，正文在 rag_document）
-- ============================================
CREATE TABLE IF NOT EXISTS vector_store (
    doc_id     TEXT PRIMARY KEY,
    embedding  vector(1024)
);

COMMENT ON TABLE  vector_store IS 'RAG 向量索引';
COMMENT ON COLUMN vector_store.doc_id IS '文档 ID（关联 rag_document.id）';
COMMENT ON COLUMN vector_store.embedding IS '文档向量（1024 维）';

-- 余弦相似度检索索引（与代码 embedding <=> ?::vector 一致）
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON vector_store USING hnsw (embedding vector_cosine_ops);


-- ============================================
-- 12. 数据字典（地区 / 资质等级 / 供应商状态 / 订单状态 / 主营品类 / 证书类型 / 物料单位）
-- ============================================

-- 供应商地区
INSERT INTO srm_dict_item (id, dict_type, label, value, sort, status, create_time, update_time, is_deleted) VALUES
(1,  'region', '华北', 'north_china',  1, 1, now(), now(), 0),
(2,  'region', '华东', 'east_china',   2, 1, now(), now(), 0),
(3,  'region', '华南', 'south_china',  3, 1, now(), now(), 0),
(4,  'region', '华中', 'central_china',4, 1, now(), now(), 0),
(5,  'region', '西南', 'southwest',    5, 1, now(), now(), 0),
(6,  'region', '西北', 'northwest',    6, 1, now(), now(), 0),
(7,  'region', '东北', 'northeast',    7, 1, now(), now(), 0);

-- 供应商资质等级
INSERT INTO srm_dict_item (id, dict_type, label, value, sort, status, create_time, update_time, is_deleted) VALUES
(11, 'qualification', '初级', '1', 1, 1, now(), now(), 0),
(12, 'qualification', '中级', '2', 2, 1, now(), now(), 0),
(13, 'qualification', '高级', '3', 3, 1, now(), now(), 0);

-- 供应商状态
INSERT INTO srm_dict_item (id, dict_type, label, value, sort, status, create_time, update_time, is_deleted) VALUES
(21, 'supplier_status', '注册',   '0', 1, 1, now(), now(), 0),
(22, 'supplier_status', '待审核', '1', 2, 1, now(), now(), 0),
(23, 'supplier_status', '已准入', '2', 3, 1, now(), now(), 0),
(24, 'supplier_status', '合作中', '3', 4, 1, now(), now(), 0),
(25, 'supplier_status', '冻结',   '4', 5, 1, now(), now(), 0),
(26, 'supplier_status', '黑名单', '5', 6, 1, now(), now(), 0);

-- 订单状态
INSERT INTO srm_dict_item (id, dict_type, label, value, sort, status, create_time, update_time, is_deleted) VALUES
(31, 'order_status', '待确认', '0', 1, 1, now(), now(), 0),
(32, 'order_status', '生产中', '1', 2, 1, now(), now(), 0),
(33, 'order_status', '已发货', '2', 3, 1, now(), now(), 0),
(34, 'order_status', '已签收', '3', 4, 1, now(), now(), 0),
(35, 'order_status', '已取消', '4', 5, 1, now(), now(), 0);

-- 主营品类（20 类）
INSERT INTO srm_dict_item (id, dict_type, label, value, sort, status, create_time, update_time, is_deleted) VALUES
(41, 'category', '电子元器件',   'electronic',         1, 1, now(), now(), 0),
(42, 'category', '钢材',         'steel',              2, 1, now(), now(), 0),
(43, 'category', '塑胶原料',     'plastic',            3, 1, now(), now(), 0),
(44, 'category', '化工原料',     'chemical',           4, 1, now(), now(), 0),
(45, 'category', '机械设备',     'machinery',          5, 1, now(), now(), 0),
(46, 'category', '包装材料',     'packaging',          6, 1, now(), now(), 0),
(47, 'category', '物流服务',     'logistics',          7, 1, now(), now(), 0),
(48, 'category', '汽车零部件',   'auto_parts',         8, 1, now(), now(), 0),
(49, 'category', '电力设备',     'power_equip',        9, 1, now(), now(), 0),
(50, 'category', '新材料',       'new_material',      10, 1, now(), now(), 0),
(51, 'category', '医药原料',     'pharma',            11, 1, now(), now(), 0),
(52, 'category', '纺织原料',     'textile',           12, 1, now(), now(), 0),
(53, 'category', '建筑材料',     'construction',      13, 1, now(), now(), 0),
(54, 'category', '食品原料',     'food',              14, 1, now(), now(), 0),
(55, 'category', '办公用品',     'office',            15, 1, now(), now(), 0),
(56, 'category', '工程机械配件', 'engineering',       16, 1, now(), now(), 0),
(57, 'category', '家用电器',     'appliance',         17, 1, now(), now(), 0),
(58, 'category', '电子材料',     'electronic_material',18, 1, now(), now(), 0),
(59, 'category', '金属制品',     'metal_product',     19, 1, now(), now(), 0),
(60, 'category', '橡胶制品',     'rubber',            20, 1, now(), now(), 0);

-- 资质证书类型（10 类）
INSERT INTO srm_dict_item (id, dict_type, label, value, sort, status, create_time, update_time, is_deleted) VALUES
(61, 'cert_type', 'ISO9001',    'ISO9001',    1, 1, now(), now(), 0),
(62, 'cert_type', 'ISO14001',   'ISO14001',   2, 1, now(), now(), 0),
(63, 'cert_type', 'IATF16949',  'IATF16949',  3, 1, now(), now(), 0),
(64, 'cert_type', 'ISO45001',   'ISO45001',   4, 1, now(), now(), 0),
(65, 'cert_type', 'CCC',        'CCC',        5, 1, now(), now(), 0),
(66, 'cert_type', 'CE',         'CE',         6, 1, now(), now(), 0),
(67, 'cert_type', 'UL',         'UL',         7, 1, now(), now(), 0),
(68, 'cert_type', 'ROHS',       'ROHS',       8, 1, now(), now(), 0),
(69, 'cert_type', 'CQC',        'CQC',        9, 1, now(), now(), 0),
(70, 'cert_type', 'CNAS',       'CNAS',      10, 1, now(), now(), 0);

-- 物料单位
INSERT INTO srm_dict_item (id, dict_type, label, value, sort, status, create_time, update_time, is_deleted) VALUES
(71, 'material_unit', '吨',   'ton',  1, 1, now(), now(), 0),
(72, 'material_unit', '个',   'pc',   2, 1, now(), now(), 0),
(73, 'material_unit', '件',   'piece',3, 1, now(), now(), 0),
(74, 'material_unit', '套',   'set',  4, 1, now(), now(), 0),
(75, 'material_unit', '桶',   'barrel',5, 1, now(), now(), 0),
(76, 'material_unit', '瓶',   'bottle',6, 1, now(), now(), 0),
(77, 'material_unit', '米',   'meter', 7, 1, now(), now(), 0),
(78, 'material_unit', '平方米','sqm',  8, 1, now(), now(), 0);


-- ============================================
-- 13. 初始管理员账号：admin / admin123
-- ============================================
INSERT INTO srm_user (id, username, password, real_name, email, phone, department, is_admin, status, create_time, update_time, is_deleted) VALUES
(1, 'admin', '$2a$12$5l9GyFv.f.lvYUOpL/nGp.49NswXTiarB/mJLQTpU2DTH8eGfYAFq', '系统管理员', 'admin@srm.com', NULL, '采购部', 1, 1, now(), now(), 0);
