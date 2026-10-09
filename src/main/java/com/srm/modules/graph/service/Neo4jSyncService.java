package com.srm.modules.graph.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.srm.modules.cert.entity.SupplierCert;
import com.srm.modules.cert.mapper.SupplierCertMapper;
import com.srm.modules.graph.repository.GraphRepository;
import com.srm.modules.order.entity.Order;
import com.srm.modules.order.mapper.OrderMapper;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.mapper.SupplierMapper;
import com.srm.modules.system.entity.DictItem;
import com.srm.modules.system.mapper.DictItemMapper;
import com.srm.modules.user.entity.User;
import com.srm.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 业务库（PostgreSQL）→ Neo4j 图谱同步服务。
 *
 * <p>把供应商 / 用户 / 采购订单从业务库以幂等方式（MERGE）同步进图谱，
 * 解决图谱数据与业务数据割裂的问题。供应商 id 与业务库 srm_supplier.id 对齐。</p>
 *
 * <p>注意：物料供应关系（SUPPLIES 的 moq/leadTime）在业务库中无直接字段，
 * 仍由 seed.cypher 演示数据提供；本服务同步供应商 / 用户 / 订单及其基础关系。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jSyncService {

    private static final Map<Integer, String> STATUS_MAP = Map.of(
            0, "注册", 1, "待审核", 2, "已准入", 3, "合作中", 4, "冻结", 5, "黑名单");
    private static final Map<Integer, String> CREDIT_MAP = Map.of(
            3, "AAA", 2, "AA", 1, "A");

    private final GraphRepository graphRepository;
    private final SupplierMapper supplierMapper;
    private final OrderMapper orderMapper;
    private final DictItemMapper dictItemMapper;
    private final UserMapper userMapper;
    private final SupplierCertMapper supplierCertMapper;

    /** 全量同步，返回各实体同步条数。 */
    public Map<String, Integer> syncAll() {
        int users = syncUsers();
        int suppliers = syncSuppliers();
        int certs = syncCert();
        int orders = syncOrders();
        log.info("[Neo4jSync] 同步完成 users={} suppliers={} certs={} orders={}", users, suppliers, certs, orders);
        return Map.of("users", users, "suppliers", suppliers, "certs", certs, "orders", orders);
    }

    // ==================== 用户 ====================

    public int syncUsers() {
        List<User> users = userMapper.selectList(null);
        for (User u : users) {
            graphRepository.write("""
                    MERGE (n:User {id: $id})
                    SET n.name = $name, n.dept = $dept
                    """, Map.of(
                    "id", u.getId(),
                    "name", u.getRealName() == null ? "" : u.getRealName(),
                    "dept", u.getDepartment() == null ? "" : u.getDepartment()));
        }
        log.info("[Neo4jSync] 同步用户 {} 个", users.size());
        return users.size();
    }

    // ==================== 供应商 ====================

    public int syncSuppliers() {
        List<Supplier> suppliers = supplierMapper.selectList(null);
        Map<String, String> regionCodeMap = loadRegionCodeMap();
        for (Supplier s : suppliers) {
            syncSupplier(s, regionCodeMap);
        }
        log.info("[Neo4jSync] 同步供应商 {} 家", suppliers.size());
        return suppliers.size();
    }

    private void syncSupplier(Supplier s, Map<String, String> regionCodeMap) {
        graphRepository.write("""
                MERGE (n:Supplier {id: $id})
                SET n.name = $name, n.status = $status, n.statusCode = $statusCode,
                    n.creditLevel = $creditLevel, n.mainCategory = $category
                """, Map.of(
                "id", s.getId(),
                "name", s.getSupplierName(),
                "status", STATUS_MAP.getOrDefault(s.getStatus(), "注册"),
                "statusCode", s.getStatus() == null ? 0 : s.getStatus(),
                "creditLevel", CREDIT_MAP.getOrDefault(s.getQualificationLevel(), "A"),
                "category", s.getMainCategory() == null ? "" : s.getMainCategory()));

        // 地域归属（优先按字典 code 对齐 seed 的 Region，保证不产生重复节点）
        if (s.getRegion() == null || s.getRegion().isBlank()) {
            return;
        }
        String code = regionCodeMap.get(s.getRegion());
        if (code != null) {
            graphRepository.write("""
                    MATCH (n:Supplier {id: $id})
                    MERGE (r:Region {code: $code}) SET r.name = $name
                    MERGE (n)-[:LOCATED_IN]->(r)
                    """, Map.of("id", s.getId(), "code", code, "name", s.getRegion()));
        } else {
            graphRepository.write("""
                    MATCH (n:Supplier {id: $id})
                    MERGE (r:Region {name: $name})
                    MERGE (n)-[:LOCATED_IN]->(r)
                    """, Map.of("id", s.getId(), "name", s.getRegion()));
        }
    }

    private Map<String, String> loadRegionCodeMap() {
        return dictItemMapper.selectList(
                        new LambdaQueryWrapper<DictItem>().eq(DictItem::getDictType, "region"))
                .stream()
                .filter(d -> d.getLabel() != null && d.getValue() != null)
                .collect(Collectors.toMap(DictItem::getLabel, DictItem::getValue, (a, b) -> a));
    }

    // ==================== 资质证书 ====================

    /** 全量同步资质证书到 Neo4j：PG 为权威源，先清空旧 Cert 节点与关系再重建。 */
    public int syncCert() {
        List<SupplierCert> certs = supplierCertMapper.selectList(null);
        graphRepository.write("MATCH (:Supplier)-[r:CERTIFIED_WITH]->(:Cert) DELETE r", Map.of());
        graphRepository.write("MATCH (c:Cert) DELETE c", Map.of());
        for (SupplierCert cert : certs) {
            syncCert(cert);
        }
        log.info("[Neo4jSync] 同步资质证书 {} 张", certs.size());
        return certs.size();
    }

    private void syncCert(SupplierCert cert) {
        graphRepository.write("""
                MERGE (s:Supplier {id: $supplierId})
                MERGE (c:Cert {certNo: $certNo})
                SET c.type = $certType, c.issueDate = $issueDate,
                    c.expireDate = $expireDate, c.status = $status
                MERGE (s)-[:CERTIFIED_WITH {status: $status}]->(c)
                """, Map.of(
                "supplierId", cert.getSupplierId(),
                "certNo", cert.getCertNo(),
                "certType", cert.getCertType() == null ? "" : cert.getCertType(),
                "issueDate", cert.getIssueDate() == null ? "" : cert.getIssueDate().toString(),
                "expireDate", cert.getExpireDate() == null ? "" : cert.getExpireDate().toString(),
                "status", cert.getStatus() == null ? "有效" : cert.getStatus()));
    }

    // ==================== 采购订单 ====================

    public int syncOrders() {
        List<Order> orders = orderMapper.selectList(null);
        for (Order o : orders) {
            syncOrder(o);
        }
        log.info("[Neo4jSync] 同步采购订单 {} 条", orders.size());
        return orders.size();
    }

    private void syncOrder(Order o) {
        // 订单节点 + 物料节点
        graphRepository.write("""
                MERGE (po:PurchaseOrder {orderNo: $orderNo})
                SET po.amount = $amount
                MERGE (m:Material {name: $materialName})
                MERGE (po)-[:CONTAINS {unitPrice: $unitPrice, qty: $qty}]->(m)
                """, Map.of(
                "orderNo", o.getOrderNo(),
                "amount", o.getTotalAmount() == null ? 0.0 : o.getTotalAmount().doubleValue(),
                "materialName", o.getMaterialName(),
                "unitPrice", o.getUnitPrice() == null ? 0.0 : o.getUnitPrice().doubleValue(),
                "qty", o.getQuantity() == null ? 0 : o.getQuantity()));

        // 订单 → 供应商
        if (o.getSupplierId() != null) {
            graphRepository.write("""
                    MATCH (po:PurchaseOrder {orderNo: $orderNo})
                    MATCH (s:Supplier {id: $supplierId})
                    MERGE (po)-[:PLACED_TO]->(s)
                    """, Map.of("orderNo", o.getOrderNo(), "supplierId", o.getSupplierId()));
        }
        // 采购员 → 订单
        if (o.getCreateBy() != null) {
            graphRepository.write("""
                    MATCH (po:PurchaseOrder {orderNo: $orderNo})
                    MERGE (u:User {id: $userId})
                    MERGE (u)-[:CREATED]->(po)
                    """, Map.of("orderNo", o.getOrderNo(), "userId", o.getCreateBy()));
        }
    }
}
