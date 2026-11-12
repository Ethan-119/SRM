package com.srm.modules.graph.service;

import com.srm.modules.graph.repository.GraphRepository;
import com.srm.modules.graph.vo.SupplierScoreVO;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 4.4 供应商综合评分。
 *
 * <p>权重：价格 35% / 质量 25% / 交期 20% / 服务 10% / 风险 10%。</p>
 *
 * <p>性能：单条评分原本需要 6 次 Neo4j 往返；批量评分 {@link #scoreBatch(List)}
 * 将其压缩为 3 次批量查询（画像 / 供应统计 / 关联风险），与供应商数量无关，
 * 避免 N 个供应商产生 6N 次跨容器查询。</p>
 */
@Service
@RequiredArgsConstructor
public class SupplierScoreService {

    private final GraphRepository graphRepository;

    /** 单供应商评分（兼容旧接口）。 */
    public SupplierScoreVO score(Long supplierId) {
        if (supplierId == null) {
            return null;
        }
        List<SupplierScoreVO> list = scoreBatch(List.of(supplierId));
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 批量综合评分。仅返回图谱中真实存在的供应商（按入参顺序）。
     */
    public List<SupplierScoreVO> scoreBatch(List<Long> supplierIds) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = supplierIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<Long, Profile> profiles = loadProfiles(ids);
        Map<Long, SupplyStats> stats = loadSupplyStats(ids);
        Map<Long, Long> related = loadRelatedCount(ids);

        List<SupplierScoreVO> result = new ArrayList<>();
        for (Long id : supplierIds) {
            if (id == null) {
                continue;
            }
            Profile p = profiles.get(id);
            if (p == null) {
                continue; // 图谱中不存在该供应商
            }
            SupplyStats st = stats.get(id);
            long rel = related.getOrDefault(id, 0L);

            double priceScore = st == null || st.priceAdvantage == null
                    ? 50.0 : priceScore(st.priceAdvantage);
            double qualityScore = qualityScore(p.creditLevel);
            double deliveryScore = st == null || st.avgLeadTime == null
                    ? 70.0 : deliveryScore(st.avgLeadTime);
            double serviceScore = 80.0; // 默认值，待接入服务反馈数据
            double riskScore = riskScore(rel, p.riskTag);

            double total = 0.35 * priceScore
                    + 0.25 * qualityScore
                    + 0.20 * deliveryScore
                    + 0.10 * serviceScore
                    + 0.10 * riskScore;

            SupplierScoreVO vo = new SupplierScoreVO();
            vo.setSupplierId(id);
            vo.setSupplierName(p.name);
            vo.setTotalScore(round(total));

            List<SupplierScoreVO.ScoreDimension> dims = new ArrayList<>();
            dims.add(dim("价格分", 0.35, priceScore, "供应商均价相对市场均价的优势"));
            dims.add(dim("质量分", 0.25, qualityScore, "信用等级代理：" + p.creditLevel));
            dims.add(dim("交期分", 0.20, deliveryScore, "平均交货周期（leadTime）"));
            dims.add(dim("服务分", 0.10, serviceScore, "默认值，待接入服务反馈数据"));
            dims.add(dim("风险分", 0.10, riskScore, "图谱关联风险 + 地域风险"));
            vo.setDimensions(dims);
            result.add(vo);
        }
        return result;
    }

    // ==================== 批量查询 ====================

    /** 一次取回 name / creditLevel / region.riskTag。 */
    private Map<Long, Profile> loadProfiles(List<Long> ids) {
        String cypher = """
                UNWIND $ids AS id
                MATCH (s:Supplier {id: id})
                OPTIONAL MATCH (s)-[:LOCATED_IN]->(r:Region)
                RETURN s.id AS id, s.name AS name,
                       s.creditLevel AS creditLevel, r.riskTag AS riskTag
                """;
        Map<Long, Profile> map = new HashMap<>();
        for (Record r : graphRepository.query(cypher, Map.of("ids", ids))) {
            Profile p = new Profile();
            p.name = str(r, "name");
            p.creditLevel = str(r, "creditLevel");
            p.riskTag = str(r, "riskTag");
            map.put(r.get("id").asLong(), p);
        }
        return map;
    }

    /** 一次取回平均交期与价格优势。 */
    private Map<Long, SupplyStats> loadSupplyStats(List<Long> ids) {
        String cypher = """
                UNWIND $ids AS id
                MATCH (s:Supplier {id: id})-[sup:SUPPLIES]->(m:Material)
                WITH id, m, toFloat(sup.price) AS myPrice, toFloat(sup.leadTime) AS leadTime
                MATCH (m)<-[sup2:SUPPLIES]-(:Supplier)
                WITH id, m, myPrice, leadTime, avg(toFloat(sup2.price)) AS marketPrice
                RETURN id AS id,
                       avg(leadTime) AS avgLeadTime,
                       avg((marketPrice - myPrice) / marketPrice) AS priceAdvantage
                """;
        Map<Long, SupplyStats> map = new HashMap<>();
        for (Record r : graphRepository.query(cypher, Map.of("ids", ids))) {
            SupplyStats st = new SupplyStats();
            st.avgLeadTime = doubleOrNull(r, "avgLeadTime");
            st.priceAdvantage = doubleOrNull(r, "priceAdvantage");
            map.put(r.get("id").asLong(), st);
        }
        return map;
    }

    /** 一次取回所有供应商的关联供应商数量。 */
    private Map<Long, Long> loadRelatedCount(List<Long> ids) {
        String cypher = """
                UNWIND $ids AS id
                MATCH path = (s:Supplier {id: id})
                  -[:SUBSIDIARY_OF|HAS_SHAREHOLDER|SHARES_EXECUTIVE_WITH*1..3]
                  -(related:Supplier)
                WHERE related <> s
                RETURN s.id AS id, count(DISTINCT related) AS relatedCount
                """;
        Map<Long, Long> map = new HashMap<>();
        for (Record r : graphRepository.query(cypher, Map.of("ids", ids))) {
            map.put(r.get("id").asLong(), r.get("relatedCount").asLong());
        }
        return map;
    }

    // ==================== 维度计算 ====================

    /** 价格分：正优势（更便宜）得分更高。 */
    private double priceScore(double advantage) {
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
    private double deliveryScore(double avgLeadTime) {
        return clamp(100 - avgLeadTime * 2, 0, 100);
    }

    /** 风险分：关联供应商越多、地域风险越高，得分越低。 */
    private double riskScore(long relatedCount, String regionRisk) {
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

    // ==================== 工具方法 ====================

    private SupplierScoreVO.ScoreDimension dim(String name, double weight, double score, String reason) {
        SupplierScoreVO.ScoreDimension d = new SupplierScoreVO.ScoreDimension();
        d.setName(name);
        d.setWeight(weight);
        d.setScore(round(score));
        d.setReason(reason);
        return d;
    }

    private String str(Record r, String key) {
        org.neo4j.driver.Value v = r.get(key);
        return (v == null || v.isNull()) ? null : v.asString();
    }

    private Double doubleOrNull(Record r, String key) {
        org.neo4j.driver.Value v = r.get(key);
        return (v == null || v.isNull()) ? null : v.asNumber().doubleValue();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ==================== 内部结构 ====================

    private static final class Profile {
        String name;
        String creditLevel;
        String riskTag;
    }

    private static final class SupplyStats {
        Double avgLeadTime;
        Double priceAdvantage;
    }
}
