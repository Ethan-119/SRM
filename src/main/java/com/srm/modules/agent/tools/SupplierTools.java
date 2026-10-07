package com.srm.modules.agent.tools;

import com.srm.modules.graph.service.GraphIntelligenceService;
import com.srm.modules.graph.vo.RelationRiskVO;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 供应商相关工具：搜索供应商、分析关联风险。
 */
@Component
@RequiredArgsConstructor
public class SupplierTools {

    private final SupplierService supplierService;
    private final GraphIntelligenceService graphIntelligenceService;

    private static final java.util.Map<Integer, String> STATUS_MAP = java.util.Map.of(
            0, "注册", 1, "待审核", 2, "已准入", 3, "合作中", 4, "冻结", 5, "黑名单");
    private static final java.util.Map<Integer, String> QUAL_MAP = java.util.Map.of(
            1, "一级", 2, "二级", 3, "三级");

    @Tool(description = "根据关键词搜索供应商，支持地区（如华东/华南）、品类（如电子/金属）、名称或编码匹配")
    public String searchSuppliers(@ToolParam(description = "搜索关键词，可为地区、品类、名称或编码") String keyword) {
        List<Supplier> suppliers = supplierService.list();
        if (suppliers.isEmpty()) {
            return "当前系统中暂无供应商数据。";
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

        if (matched.isEmpty()) {
            return "未找到与「" + kw + "」相关的供应商。可尝试使用地区名（华东/华南/华北）、品类关键词（电子/机械/金属）或供应商名称。";
        }

        StringBuilder sb = new StringBuilder("匹配到的供应商信息如下：\n");
        int limit = Math.min(matched.size(), 20);
        for (int i = 0; i < limit; i++) {
            Supplier s = matched.get(i);
            sb.append("- 编码: ").append(s.getSupplierCode())
                    .append(", 名称: ").append(s.getSupplierName())
                    .append(", 地区: ").append(nullTo(s.getRegion()))
                    .append(", 主营品类: ").append(nullTo(s.getMainCategory()))
                    .append(", 资质: ").append(QUAL_MAP.getOrDefault(s.getQualificationLevel(), "未知"))
                    .append(", 状态: ").append(STATUS_MAP.getOrDefault(s.getStatus(), "未知"))
                    .append('\n');
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

    private boolean contains(String source, String kw) {
        return source != null && kw != null && source.toLowerCase().contains(kw.toLowerCase());
    }

    private String nullTo(String s) {
        return s == null ? "" : s;
    }
}
