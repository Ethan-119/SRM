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
