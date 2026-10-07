package com.srm.modules.graph.service;

import com.srm.modules.graph.repository.GraphRepository;
import com.srm.modules.graph.vo.AlternativeSupplierVO;
import com.srm.modules.graph.vo.BuyerProfileVO;
import com.srm.modules.graph.vo.ConcentrationRiskVO;
import com.srm.modules.graph.vo.RelationRiskVO;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 图谱智能分析服务。
 * 对应改造方案 4.1 / 4.2 / 4.3 / 4.6 四大能力。
 */
@Service
@RequiredArgsConstructor
public class GraphIntelligenceService {

    private final GraphRepository graphRepository;

    /**
     * 4.1 供应链集中度风险分析（赫芬达尔指数）。
     * 注：price/moq 使用 toFloat 保证浮点除法，避免整数除法截断。
     */
    public List<ConcentrationRiskVO> analyzeConcentration(String materialName) {
        String cypher = """
                MATCH (m:Material {name: $materialName})<-[s:SUPPLIES]-(sup:Supplier)
                WHERE sup.status IN ['已准入', '合作中']
                WITH m, sum(toFloat(s.price) * toFloat(s.moq)) AS totalValue
                MATCH (m)<-[s2:SUPPLIES]-(sup2:Supplier)
                WHERE sup2.status IN ['已准入', '合作中']
                WITH m, sum(((toFloat(s2.price) * toFloat(s2.moq)) / totalValue) *
                            ((toFloat(s2.price) * toFloat(s2.moq)) / totalValue)) AS hhi,
                     count(DISTINCT sup2) AS supplierCount
                RETURN m.name AS materialName, supplierCount, hhi,
                  CASE WHEN hhi > 0.25 THEN '高风险：高度集中'
                       WHEN hhi > 0.15 THEN '中风险：中度集中'
                       ELSE '低风险：分散' END AS riskLevel
                """;
        List<Record> records = graphRepository.query(cypher, Map.of("materialName", materialName));
        return records.stream().map(r -> {
            ConcentrationRiskVO vo = new ConcentrationRiskVO();
            vo.setMaterialName(r.get("materialName").asString());
            vo.setSupplierCount(r.get("supplierCount").asLong());
            vo.setHhi(r.get("hhi").asNumber().doubleValue());
            vo.setRiskLevel(r.get("riskLevel").asString());
            return vo;
        }).toList();
    }

    /**
     * 4.2 替代供应商发现：与目标供应商供应相同物料、且状态合规的供应商，按价格差排序。
     */
    public List<AlternativeSupplierVO> findAlternativeSuppliers(Long targetId) {
        String cypher = """
                MATCH (target:Supplier {id: $targetId})-[s1:SUPPLIES]->(m:Material)
                MATCH (alt:Supplier)-[s2:SUPPLIES]->(m)
                WHERE alt <> target AND alt.status IN ['已准入', '合作中']
                WITH alt, m, abs(toFloat(s1.price) - toFloat(s2.price)) / toFloat(s1.price) AS priceDiff
                RETURN alt.name AS supplierName, collect(m.name) AS materials,
                       avg(priceDiff) AS priceDiffAvg, alt.creditLevel AS creditLevel
                ORDER BY avg(priceDiff) ASC
                """;
        List<Record> records = graphRepository.query(cypher, Map.of("targetId", targetId));
        return records.stream().map(r -> {
            AlternativeSupplierVO vo = new AlternativeSupplierVO();
            vo.setSupplierName(r.get("supplierName").asString());
            vo.setMaterials(r.get("materials").asList(v -> v.asString()));
            vo.setPriceDiffAvg(r.get("priceDiffAvg").asNumber().doubleValue());
            vo.setCreditLevel(r.get("creditLevel").asString());
            return vo;
        }).toList();
    }

    /**
     * 4.3 关联风险穿透（多层）：沿股权 / 股东 / 高管关联关系向外穿透。
     */
    public List<RelationRiskVO> penetrateRelationRisk(Long targetId, int maxDepth) {
        int depth = (maxDepth <= 0) ? 3 : maxDepth;
        String cypher = """
                MATCH path = (target:Supplier {id: $targetId})
                  -[:SUBSIDIARY_OF|HAS_SHAREHOLDER|SHARES_EXECUTIVE_WITH*1..%d]
                  -(related:Supplier)
                WHERE related <> target
                RETURN related.name AS relatedName, length(path) AS depth,
                  [r IN relationships(path) | type(r)] AS relationChain
                """.formatted(depth);
        List<Record> records = graphRepository.query(cypher, Map.of("targetId", targetId));
        return records.stream().map(r -> {
            RelationRiskVO vo = new RelationRiskVO();
            vo.setRelatedName(r.get("relatedName").asString());
            vo.setDepth(r.get("depth").asLong());
            vo.setRelationChain(r.get("relationChain").asList(v -> v.asString()));
            return vo;
        }).toList();
    }

    /**
     * 4.6 采购员画像：供应商合作网络 + 品类覆盖 + 议价能力。
     */
    public BuyerProfileVO buildBuyerProfile(Long userId) {
        BuyerProfileVO vo = new BuyerProfileVO();
        vo.setUserId(userId);

        // 用户名
        List<Record> userRecords = graphRepository.query(
                "MATCH (u:User {id: $userId}) RETURN u.name AS userName",
                Map.of("userId", userId));
        if (!userRecords.isEmpty()) {
            vo.setUserName(userRecords.get(0).get("userName").asString());
        }

        // 1) 供应商合作网络
        String networkCypher = """
                MATCH (u:User {id: $userId})-[:CREATED]->(o:PurchaseOrder)-[:PLACED_TO]->(s:Supplier)
                RETURN s.name AS supplierName, count(o) AS orderCount, sum(o.amount) AS totalAmount
                ORDER BY orderCount DESC
                """;
        vo.setSupplierNetwork(graphRepository.query(networkCypher, Map.of("userId", userId)).stream()
                .map(r -> {
                    BuyerProfileVO.SupplierNetworkItem item = new BuyerProfileVO.SupplierNetworkItem();
                    item.setSupplierName(r.get("supplierName").asString());
                    item.setOrderCount(r.get("orderCount").asLong());
                    item.setTotalAmount(r.get("totalAmount").asNumber().doubleValue());
                    return item;
                }).toList());

        // 2) 品类覆盖与偏好
        String categoryCypher = """
                MATCH (u:User {id: $userId})-[:CREATED]->(o:PurchaseOrder)-[:CONTAINS]->(m:Material)
                RETURN m.category AS category, m.name AS material, count(*) AS count
                ORDER BY count DESC
                """;
        vo.setCategoryCoverage(graphRepository.query(categoryCypher, Map.of("userId", userId)).stream()
                .map(r -> {
                    BuyerProfileVO.CategoryItem item = new BuyerProfileVO.CategoryItem();
                    item.setCategory(r.get("category").asString());
                    item.setMaterial(r.get("material").asString());
                    item.setCount(r.get("count").asLong());
                    return item;
                }).toList());

        // 3) 议价能力：我的采购价 vs 市场均价
        String bargainCypher = """
                MATCH (u:User {id: $userId})-[:CREATED]->(o:PurchaseOrder)-[c:CONTAINS]->(m:Material)
                WITH m, avg(c.unitPrice) AS myPrice
                MATCH (m)<-[c2:CONTAINS]-(:PurchaseOrder)
                WITH m, myPrice, avg(c2.unitPrice) AS marketPrice
                RETURN m.name AS materialName, myPrice, marketPrice,
                       (marketPrice - myPrice) / marketPrice AS savingRate
                ORDER BY savingRate DESC
                """;
        vo.setBargaining(graphRepository.query(bargainCypher, Map.of("userId", userId)).stream()
                .map(r -> {
                    BuyerProfileVO.BargainItem item = new BuyerProfileVO.BargainItem();
                    item.setMaterialName(r.get("materialName").asString());
                    item.setMyPrice(r.get("myPrice").asNumber().doubleValue());
                    item.setMarketPrice(r.get("marketPrice").asNumber().doubleValue());
                    item.setSavingRate(r.get("savingRate").asNumber().doubleValue());
                    return item;
                }).toList());

        return vo;
    }
}
