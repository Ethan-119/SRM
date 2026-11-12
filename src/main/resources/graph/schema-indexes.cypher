// ============================================
// SRM 图谱索引自动初始化（幂等，应用启动时执行）
// 只创建评分/检索/关联穿透链路真正需要的约束与索引。
// 主动清理两条历史遗留死约束：
//   - material_id：物料同步按 name 建节点、id 恒为 null，唯一约束无效且埋雷；
//   - factory_name：Factory 节点已不再生成（seed/sync 均无）。
// person_hash 保留：股东关联穿透靠 Person(nameHash) 去重，seed 仍在生成。
// ============================================

// ---- 清理历史遗留死约束（幂等）----
DROP CONSTRAINT material_id IF EXISTS;
DROP CONSTRAINT factory_name IF EXISTS;

// ---- 唯一约束 ----
CREATE CONSTRAINT supplier_id IF NOT EXISTS FOR (s:Supplier) REQUIRE s.id IS UNIQUE;
CREATE CONSTRAINT region_code IF NOT EXISTS FOR (r:Region) REQUIRE r.code IS UNIQUE;
CREATE CONSTRAINT cert_no IF NOT EXISTS FOR (c:Cert) REQUIRE c.certNo IS UNIQUE;
CREATE CONSTRAINT person_hash IF NOT EXISTS FOR (p:Person) REQUIRE p.nameHash IS UNIQUE;
CREATE CONSTRAINT order_no IF NOT EXISTS FOR (o:PurchaseOrder) REQUIRE o.orderNo IS UNIQUE;
CREATE CONSTRAINT user_id IF NOT EXISTS FOR (u:User) REQUIRE u.id IS UNIQUE;

CREATE INDEX supplier_name IF NOT EXISTS FOR (s:Supplier) ON (s.name);
CREATE INDEX supplier_status IF NOT EXISTS FOR (s:Supplier) ON (s.status);
CREATE INDEX supplier_credit IF NOT EXISTS FOR (s:Supplier) ON (s.creditLevel);
CREATE INDEX material_name IF NOT EXISTS FOR (m:Material) ON (m.name);
CREATE INDEX material_category IF NOT EXISTS FOR (m:Material) ON (m.category);
CREATE INDEX region_risk IF NOT EXISTS FOR (r:Region) ON (r.riskTag);
CREATE INDEX cert_expire IF NOT EXISTS FOR (c:Cert) ON (c.expireDate);
