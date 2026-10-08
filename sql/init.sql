-- ============================================
-- SRM 供应商管理系统 - 数据库初始化脚本（PostgreSQL）
-- 执行前先创建数据库：createdb srm （或 psql -c "CREATE DATABASE srm"）
-- 然后连接该库执行：psql -d srm -f sql/init.sql
-- ============================================

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
CREATE INDEX idx_region ON srm_supplier (region);
CREATE INDEX idx_status ON srm_supplier (status);
CREATE INDEX idx_is_deleted ON srm_supplier (is_deleted);

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
CREATE INDEX idx_supplier_id ON srm_purchase_order (supplier_id);
CREATE INDEX idx_status ON srm_purchase_order (status);
CREATE INDEX idx_delivery_date ON srm_purchase_order (delivery_date);
CREATE INDEX idx_is_deleted ON srm_purchase_order (is_deleted);

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
CREATE INDEX idx_is_deleted ON srm_dict_item (is_deleted);

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
-- 4. 初始化字典数据
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


-- ============================================
-- 5. 管理端用户表
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
CREATE INDEX idx_is_admin ON srm_user (is_admin);
CREATE INDEX idx_is_deleted ON srm_user (is_deleted);

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

-- 初始管理员账号: admin / admin123
INSERT INTO srm_user (id, username, password, real_name, email, phone, department, is_admin, status, create_time, update_time, is_deleted) VALUES
(1, 'admin', '$2a$12$5l9GyFv.f.lvYUOpL/nGp.49NswXTiarB/mJLQTpU2DTH8eGfYAFq', '系统管理员', 'admin@srm.com', NULL, '采购部', 1, 1, now(), now(), 0);


-- ============================================
-- 6. AI 会话历史表（长期记忆，配合 Redis 短期窗口）
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
