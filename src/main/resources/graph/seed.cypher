// ============================================
// SRM 供应商知识图谱 - 示例种子数据
// 演示 8 类节点 + 12 类关系的写法，实际数据由业务库同步脚本生成
// ============================================
//
// 字段映射约定（与业务 PostgreSQL 库 srm 对齐）：
//   Supplier.id   = srm.srm_supplier.id
//   Supplier.name = srm.srm_supplier.supplier_name
//   Supplier.status = 中文状态标签（'待审核' / '已准入' / '合作中' / '冻结' / '黑名单'）
//   Supplier.statusCode = 数值状态（0-5），用于与业务库同步对齐
//   Material.id   = 业务物料编码
//   PurchaseOrder.orderNo = srm.srm_purchase_order.order_no
//   Region.code   = srm.srm_dict_item.value（region 字典）

// ---- 地域 ----
MERGE (rEast:Region {code: 'east_china'})    SET rEast.name = '华东', rEast.level = '低', rEast.riskTag = '稳定';
MERGE (rSouth:Region {code: 'south_china'})  SET rSouth.name = '华南', rSouth.level = '低', rSouth.riskTag = '稳定';
MERGE (rNorth:Region {code: 'north_china'})  SET rNorth.name = '华北', rNorth.level = '中', rNorth.riskTag = '关注';
MERGE (rCentral:Region {code: 'central_china'}) SET rCentral.name = '华中', rCentral.level = '低', rCentral.riskTag = '稳定';
MERGE (rNortheast:Region {code: 'northeast'}) SET rNortheast.name = '东北', rNortheast.level = '低', rNortheast.riskTag = '稳定';

// ---- 供应商（id 对齐业务库 srm_supplier.id）----
MERGE (a:Supplier {id: 1})  SET a.name = '深圳华强电子科技有限公司', a.status = '合作中', a.creditLevel = 'AAA', a.regCapital = 8000, a.statusCode = 3;
MERGE (b:Supplier {id: 2})  SET b.name = '上海宝钢精密钢材有限公司', b.status = '合作中', b.creditLevel = 'AAA', b.regCapital = 20000, b.statusCode = 3;
MERGE (c:Supplier {id: 7})  SET c.name = '武汉光谷光电科技有限公司', c.status = '合作中', c.creditLevel = 'AA',  c.regCapital = 5000, c.statusCode = 3;
MERGE (d:Supplier {id: 15}) SET d.name = '济南钢铁集团销售有限公司', d.status = '待审核', d.creditLevel = 'AA',  d.regCapital = 6000, d.statusCode = 1;
MERGE (e:Supplier {id: 22}) SET e.name = '合肥中科量子信息技术有限公司', e.status = '合作中', e.creditLevel = 'AAA', e.regCapital = 3000, e.statusCode = 3;
MERGE (f:Supplier {id: 23}) SET f.name = '大连船舶重工配套有限公司', f.status = '合作中', f.creditLevel = 'AA',  f.regCapital = 4000, f.statusCode = 3;
MERGE (g:Supplier {id: 24}) SET g.name = '无锡华润微电子有限公司', g.status = '合作中', g.creditLevel = 'AAA', g.regCapital = 10000, g.statusCode = 3;
MERGE (h:Supplier {id: 39}) SET h.name = '太原钢铁集团有限公司', h.status = '待审核', h.creditLevel = 'AAA', h.regCapital = 15000, h.statusCode = 1;

// ---- 物料 ----
MERGE (m1:Material {id: 'MAT-CRS'}) SET m1.name = '冷轧钢板', m1.category = '金属', m1.spec = '1.0mm', m1.unit = '吨';
MERGE (m2:Material {id: 'MAT-SS'})  SET m2.name = '不锈钢板', m2.category = '金属', m2.spec = '2.0mm', m2.unit = '吨';
MERGE (m3:Material {id: 'MAT-MCU'}) SET m3.name = 'STM32单片机', m3.category = '电子', m3.spec = 'F103', m3.unit = '个';

// ---- 供应关系（price 对齐业务库订单单价）----
MERGE (b)-[:SUPPLIES {price: 8500,  moq: 50,  leadTime: 15, currency: 'CNY'}]->(m1);
MERGE (d)-[:SUPPLIES {price: 8300,  moq: 100, leadTime: 20, currency: 'CNY'}]->(m1);
MERGE (f)-[:SUPPLIES {price: 8600,  moq: 80,  leadTime: 25, currency: 'CNY'}]->(m1);
MERGE (b)-[:SUPPLIES {price: 18500, moq: 25,  leadTime: 15, currency: 'CNY'}]->(m2);
MERGE (h)-[:SUPPLIES {price: 18000, moq: 50,  leadTime: 30, currency: 'CNY'}]->(m2);
MERGE (a)-[:SUPPLIES {price: 32,    moq: 500, leadTime: 8,  currency: 'CNY'}]->(m3);
MERGE (c)-[:SUPPLIES {price: 35,    moq: 300, leadTime: 10, currency: 'CNY'}]->(m3);
MERGE (g)-[:SUPPLIES {price: 30,    moq: 1000, leadTime: 12, currency: 'CNY'}]->(m3);

// ---- 地域归属 ----
MERGE (a)-[:LOCATED_IN]->(rSouth);
MERGE (b)-[:LOCATED_IN]->(rEast);
MERGE (c)-[:LOCATED_IN]->(rCentral);
MERGE (d)-[:LOCATED_IN]->(rNorth);
MERGE (e)-[:LOCATED_IN]->(rCentral);
MERGE (f)-[:LOCATED_IN]->(rNortheast);
MERGE (g)-[:LOCATED_IN]->(rEast);
MERGE (h)-[:LOCATED_IN]->(rNorth);

// ---- 股权 / 高管关联（关联风险穿透演示）----
MERGE (p1:Person {nameHash: 'p_zhangsan', idCardHash: 'idc_zhangsan', role: '法人'});
MERGE (h)-[:SUBSIDIARY_OF {shareRatio: 0.51}]->(b);
MERGE (b)-[:HAS_SHAREHOLDER {ratio: 0.4}]->(p1);
MERGE (p1)-[:HOLDS_POSITION {title: '董事长'}]->(b);
MERGE (p1)-[:SHARES_EXECUTIVE_WITH {role: '董事'}]->(d);

// ---- 竞争关系 ----
MERGE (c)-[:COMPETES_WITH {material: '电子元器件', intensity: '中'}]->(g);

// ---- 资质证书（含 30 天内到期与已过期两种场景）----
MERGE (cert1:Cert {certNo: 'ISO9001-A'}) SET cert1.type = 'ISO9001', cert1.issueDate = '2023-01-01', cert1.expireDate = '2026-10-20', cert1.status = '有效';
MERGE (cert2:Cert {certNo: 'ISO9001-C'}) SET cert2.type = 'ISO9001', cert2.issueDate = '2022-06-01', cert2.expireDate = '2025-06-30', cert2.status = '过期';
MERGE (b)-[:CERTIFIED_WITH {status: '有效'}]->(cert1);
MERGE (d)-[:CERTIFIED_WITH {status: '过期'}]->(cert2);

// ---- 工厂 ----
MERGE (f1:Factory {name: '上海宝钢精密钢材工厂'}) SET f1.address = '上海市宝山区宝钢一路188号', f1.capacity = 10000, f1.employeeCount = 300;
MERGE (f1)-[:BELONGS_TO]->(b);

// ---- 用户 / 采购订单（采购员画像演示，对齐业务库）----
MERGE (u1:User {id: 1}) SET u1.name = '系统管理员', u1.dept = '采购部';
MERGE (o1:PurchaseOrder {orderNo: 'PO202405002'}) SET o1.amount = 425000, o1.status = '生产中', o1.createTime = '2024-05-12', o1.deliveryDate = '2024-07-25';
MERGE (o2:PurchaseOrder {orderNo: 'PO202401002'}) SET o2.amount = 462500, o2.status = '已签收', o2.createTime = '2024-01-08', o2.deliveryDate = '2024-03-05';
MERGE (u1)-[:CREATED]->(o1)-[:PLACED_TO]->(b);
MERGE (u1)-[:CREATED]->(o2)-[:PLACED_TO]->(b);
MERGE (o1)-[:CONTAINS {qty: 50, unitPrice: 8500}]->(m1);
MERGE (o2)-[:CONTAINS {qty: 25, unitPrice: 18500}]->(m2);
