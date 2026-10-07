// ============================================
// SRM 供应商知识图谱 - 约束与索引初始化脚本
// 在 Neo4j Browser / cypher-shell 中执行一次即可
// ============================================

// ---- 唯一约束（保证 id 唯一，避免重复节点）----
CREATE CONSTRAINT supplier_id IF NOT EXISTS FOR (s:Supplier) REQUIRE s.id IS UNIQUE;
CREATE CONSTRAINT material_id IF NOT EXISTS FOR (m:Material) REQUIRE m.id IS UNIQUE;
CREATE CONSTRAINT region_code IF NOT EXISTS FOR (r:Region) REQUIRE r.code IS UNIQUE;
CREATE CONSTRAINT cert_no IF NOT EXISTS FOR (c:Cert) REQUIRE c.certNo IS UNIQUE;
CREATE CONSTRAINT person_hash IF NOT EXISTS FOR (p:Person) REQUIRE p.nameHash IS UNIQUE;
CREATE CONSTRAINT factory_name IF NOT EXISTS FOR (f:Factory) REQUIRE f.name IS UNIQUE;
CREATE CONSTRAINT order_no IF NOT EXISTS FOR (o:PurchaseOrder) REQUIRE o.orderNo IS UNIQUE;
CREATE CONSTRAINT user_id IF NOT EXISTS FOR (u:User) REQUIRE u.id IS UNIQUE;

// ---- 索引（加速常用过滤）----
CREATE INDEX supplier_name IF NOT EXISTS FOR (s:Supplier) ON (s.name);
CREATE INDEX supplier_status IF NOT EXISTS FOR (s:Supplier) ON (s.status);
CREATE INDEX supplier_credit IF NOT EXISTS FOR (s:Supplier) ON (s.creditLevel);
CREATE INDEX material_name IF NOT EXISTS FOR (m:Material) ON (m.name);
CREATE INDEX material_category IF NOT EXISTS FOR (m:Material) ON (m.category);
CREATE INDEX region_risk IF NOT EXISTS FOR (r:Region) ON (r.riskTag);
CREATE INDEX cert_expire IF NOT EXISTS FOR (c:Cert) ON (c.expireDate);
