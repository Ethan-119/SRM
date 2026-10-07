package com.srm.modules.graph.service;

import com.srm.modules.graph.repository.GraphRepository;
import com.srm.modules.graph.vo.SupplierScoreVO;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 4.4 供应商综合评分。
 *
 * 权重：价格 35% / 质量 25% / 交期 20% / 服务 10% / 风险 10%。
 * 其中「价格 / 交期 / 风险」由 Neo4j 图谱实时计算；
 * 「质量 / 服务」当前使用信用等级等作为代理指标，后续接入检验合格率、退货率、
 * 准时交货率、服务响应等数据源后替换，仅需实现对应 fetchXxx 方法即可。
 */
@Service
@RequiredArgsConstructor
public class SupplierScoreService {

    private final GraphRepository graphRepository;

    public SupplierScoreVO score(Long supplierId) {
        String name = fetchName(supplierId);
        String creditLevel = fetchCreditLevel(supplierId);

        // 各维度得分
        double priceScore = priceScore(supplierId);
        double qualityScore = qualityScore(creditLevel);
        double deliveryScore = deliveryScore(supplierId);
        double serviceScore = 80.0; // 默认值，待接入服务反馈数据
        double riskScore = riskScore(supplierId);

        double total = 0.35 * priceScore
                + 0.25 * qualityScore
                + 0.20 * deliveryScore
                + 0.10 * serviceScore
                + 0.10 * riskScore;

        SupplierScoreVO vo = new SupplierScoreVO();
        vo.setSupplierId(supplierId);
        vo.setSupplierName(name);
        vo.setTotalScore(round(total));

        List<SupplierScoreVO.ScoreDimension> dims = new ArrayList<>();
        dims.add(dim("价格分", 0.35, priceScore, "供应商均价相对市场均价的优势"));
        dims.add(dim("质量分", 0.25, qualityScore, "信用等级代理：" + creditLevel));
        dims.add(dim("交期分", 0.20, deliveryScore, "平均交货周期（leadTime）"));
        dims.add(dim("服务分", 0.10, serviceScore, "默认值，待接入服务反馈数据"));
        dims.add(dim("风险分", 0.10, riskScore, "图谱关联风险 + 地域风险"));
        vo.setDimensions(dims);
        return vo;
    }

    // ==================== 各维度 ====================

    /** 价格分：供应商均价 vs 市场均价，正优势（更便宜）得分更高。 */
    private double priceScore(Long supplierId) {
        String cypher = """
                MATCH (s:Supplier {id: $supplierId})-[sup:SUPPLIES]->(m:Material)
                WITH m, toFloat(sup.price) AS myPrice
                MATCH (m)<-[sup2:SUPPLIES]-(:Supplier)
                WITH m, myPrice, avg(toFloat(sup2.price)) AS marketPrice
                RETURN avg((marketPrice - myPrice) / marketPrice) AS priceAdvantage
                """;
        Double advantage = firstDouble(cypher, "priceAdvantage", Map.of("supplierId", supplierId));
        if (advantage == null) {
            return 50.0; // 无供应数据，取中性分
        }
        return clamp(50 + advantage * 100, 0, 100);
    }

    /** 质量分：当前用信用等级作为代理。 */
    private double qualityScore(String creditLevel) {
        if (creditLevel == null) {
            return 60.0;
        }
        return switch (creditLevel.toUpperCase()) {
            case "AAA" -> 95.0;
            case "AA" -> 85.0;
            case "A" -> 75.0;
            case "BBB" -> 65.0;
            default -> 60.0;
        };
    }

    /** 交期分：平均 leadTime 越短得分越高。 */
    private double deliveryScore(Long supplierId) {
        String cypher = """
                MATCH (s:Supplier {id: $supplierId})-[sup:SUPPLIES]->(m:Material)
                RETURN avg(toFloat(sup.leadTime)) AS avgLeadTime
                """;
        Double avgLeadTime = firstDouble(cypher, "avgLeadTime", Map.of("supplierId", supplierId));
        if (avgLeadTime == null) {
            return 70.0;
        }
        return clamp(100 - avgLeadTime * 2, 0, 100);
    }

    /** 风险分：关联供应商越多、地域风险越高，得分越低。 */
    private double riskScore(Long supplierId) {
        long relatedCount = countRelated(supplierId);
        String regionRisk = regionRisk(supplierId);

        double score = clamp(100 - relatedCount * 15, 0, 100);
        if (regionRisk != null) {
            score -= switch (regionRisk) {
                case "高风险" -> 20;
                case "关注" -> 10;
                default -> 0;
            };
        }
        return clamp(score, 0, 100);
    }

    // ==================== 基础查询辅助 ====================

    private String fetchName(Long supplierId) {
        List<Record> r = graphRepository.query(
                "MATCH (s:Supplier {id: $id}) RETURN s.name AS name",
                Map.of("id", supplierId));
        return r.isEmpty() ? null : r.get(0).get("name").asString();
    }

    private String fetchCreditLevel(Long supplierId) {
        List<Record> r = graphRepository.query(
                "MATCH (s:Supplier {id: $id}) RETURN s.creditLevel AS creditLevel",
                Map.of("id", supplierId));
        return r.isEmpty() ? null : r.get(0).get("creditLevel").asString();
    }

    private long countRelated(Long supplierId) {
        String cypher = """
                MATCH path = (s:Supplier {id: $id})
                  -[:SUBSIDIARY_OF|HAS_SHAREHOLDER|SHARES_EXECUTIVE_WITH*1..3]
                  -(related:Supplier)
                WHERE related <> s
                RETURN count(DISTINCT related) AS relatedCount
                """;
        List<Record> r = graphRepository.query(cypher, Map.of("id", supplierId));
        return r.isEmpty() ? 0 : r.get(0).get("relatedCount").asLong();
    }

    private String regionRisk(Long supplierId) {
        List<Record> r = graphRepository.query(
                "MATCH (s:Supplier {id: $id})-[:LOCATED_IN]->(reg:Region) RETURN reg.riskTag AS riskTag",
                Map.of("id", supplierId));
        return r.isEmpty() ? null : r.get(0).get("riskTag").asString();
    }

    private Double firstDouble(String cypher, String key, Map<String, Object> params) {
        List<Record> r = graphRepository.query(cypher, params);
        if (r.isEmpty() || r.get(0).get(key).isNull()) {
            return null;
        }
        return r.get(0).get(key).asNumber().doubleValue();
    }

    private SupplierScoreVO.ScoreDimension dim(String name, double weight, double score, String reason) {
        SupplierScoreVO.ScoreDimension d = new SupplierScoreVO.ScoreDimension();
        d.setName(name);
        d.setWeight(weight);
        d.setScore(round(score));
        d.setReason(reason);
        return d;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
