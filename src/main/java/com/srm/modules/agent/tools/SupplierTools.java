package com.srm.modules.agent.tools;

import com.srm.modules.graph.service.GraphIntelligenceService;
import com.srm.modules.graph.service.SupplierScoreService;
import com.srm.modules.graph.vo.RelationRiskVO;
import com.srm.modules.graph.vo.SupplierScoreVO;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 供应商相关工具：搜索供应商、分析关联风险、搜索+评分对比。
 */
@Component
@RequiredArgsConstructor
public class SupplierTools {

    private final SupplierService supplierService;
    private final GraphIntelligenceService graphIntelligenceService;
    private final SupplierScoreService supplierScoreService;

    private static final Map<Integer, String> STATUS_MAP = Map.of(
            0, "注册", 1, "待审核", 2, "已准入", 3, "合作中", 4, "冻结", 5, "黑名单");
    private static final Map<Integer, String> QUAL_MAP = Map.of(
            1, "一级", 2, "二级", 3, "三级");

    @Tool(description = "根据关键词搜索供应商，支持地区（如华东/华南）、品类（如电子/金属）、名称或编码匹配")
    public String searchSuppliers(@ToolParam(description = "搜索关键词，可为地区、品类、名称或编码") String keyword) {
        List<Supplier> matched = matchSuppliers(keyword);
        if (matched.isEmpty()) {
            return "未找到与「" + (keyword == null ? "" : keyword.trim()) + "」相关的供应商。"
                    + "可尝试使用地区名（华东/华南/华北）、品类关键词（电子/机械/金属）或供应商名称。";
        }
        StringBuilder sb = new StringBuilder("匹配到的供应商信息如下：\n");
        int limit = Math.min(matched.size(), 20);
        for (int i = 0; i < limit; i++) {
            Supplier s = matched.get(i);
            sb.append("- ID: ").append(s.getId())
                    .append(", 编码: ").append(s.getSupplierCode())
                    .append(", 名称: ").append(s.getSupplierName())
                    .append(", 地区: ").append(nullTo(s.getRegion()))
                    .append(", 主营品类: ").append(nullTo(s.getMainCategory()))
                    .append(", 资质: ").append(QUAL_MAP.getOrDefault(s.getQualificationLevel(), "未知"))
                    .append(", 状态: ").append(STATUS_MAP.getOrDefault(s.getStatus(), "未知"))
                    .append('\n');
        }
        return sb.toString();
    }

    @Tool(description = "按关键词搜索供应商并一次性返回各供应商综合评分对比（搜索+评分一步完成），适合“找出某地区/某品类的供应商并对比评分”类问题")
    public String searchAndCompareSuppliers(@ToolParam(description = "搜索关键词，可为地区（如华南）、品类、名称或编码") String keyword) {
        List<Supplier> matched = matchSuppliers(keyword);
        if (matched.isEmpty()) {
            return "未找到与「" + (keyword == null ? "" : keyword.trim()) + "」相关的供应商。";
        }
        int limit = Math.min(matched.size(), 20);
        List<Supplier> top = matched.subList(0, limit);
        List<Long> ids = top.stream().map(Supplier::getId).toList();
        Map<Long, SupplierScoreVO> scoreMap = supplierScoreService.scoreBatch(ids).stream()
                .collect(Collectors.toMap(SupplierScoreVO::getSupplierId, v -> v, (a, b) -> a));

        StringBuilder sb = new StringBuilder("供应商及综合评分对比（关键词「")
                .append(keyword == null ? "" : keyword.trim()).append("」）：\n");
        for (Supplier s : top) {
            SupplierScoreVO vo = scoreMap.get(s.getId());
            sb.append("- ID ").append(s.getId())
                    .append("，").append(s.getSupplierName())
                    .append("（").append(nullTo(s.getRegion()))
                    .append(" / ").append(nullTo(s.getMainCategory())).append("）");
            if (vo == null || vo.getTotalScore() == null) {
                sb.append("：暂无评分数据");
            } else {
                sb.append("：总分 ").append(fmt(vo.getTotalScore()));
                if (vo.getDimensions() != null && !vo.getDimensions().isEmpty()) {
                    sb.append("（");
                    sb.append(vo.getDimensions().stream()
                            .map(d -> d.getName() + " " + fmt(d.getScore()))
                            .collect(Collectors.joining("，")));
                    sb.append("）");
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Tool(description = "分析指定供应商的关联风险（股权/股东/高管多层穿透），返回关联企业列表")
    public String analyzeSupplierRisk(@ToolParam(description = "供应商ID") Long supplierId) {
        List<RelationRiskVO> risks = graphIntelligenceService.penetrateRelationRisk(supplierId, 3);
        if (risks.isEmpty()) {
            return "该供应商未发现股权/股东/高管层面的关联企业。";
        }
        StringBuilder sb = new StringBuilder("供应商关联风险分析结果：\n");
        for (RelationRiskVO r : risks) {
            sb.append("- ").append(r.getRelatedName())
                    .append("（关联深度 ").append(r.getDepth())
                    .append(" 层，关系链: ").append(String.join(" -> ", r.getRelationChain()))
                    .append("）\n");
        }
        sb.append("提示：存在关联企业可能带来集中度或串标风险，建议重点关注。");
        return sb.toString();
    }

    private List<Supplier> matchSuppliers(String keyword) {
        List<Supplier> suppliers = supplierService.list();
        if (suppliers.isEmpty()) {
            return List.of();
        }
        String kw = keyword == null ? "" : keyword.trim();
        List<Supplier> matched = new ArrayList<>();
        for (Supplier s : suppliers) {
            if (kw.isEmpty()
                    || contains(s.getSupplierName(), kw)
                    || contains(s.getSupplierCode(), kw)
                    || contains(s.getRegion(), kw)
                    || contains(s.getMainCategory(), kw)) {
                matched.add(s);
            }
        }
        return matched;
    }

    private boolean contains(String source, String kw) {
        return source != null && kw != null && source.toLowerCase().contains(kw.toLowerCase());
    }

    private String nullTo(String s) {
        return s == null ? "" : s;
    }

    private String fmt(Double v) {
        return v == null ? "N/A" : String.format("%.2f", v);
    }
}
