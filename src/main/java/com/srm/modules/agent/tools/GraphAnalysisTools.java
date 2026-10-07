package com.srm.modules.agent.tools;

import com.srm.modules.graph.service.GraphIntelligenceService;
import com.srm.modules.graph.service.SupplierScoreService;
import com.srm.modules.graph.vo.AlternativeSupplierVO;
import com.srm.modules.graph.vo.BuyerProfileVO;
import com.srm.modules.graph.vo.ConcentrationRiskVO;
import com.srm.modules.graph.vo.SupplierScoreVO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 图谱智能分析工具：把 Neo4j 分析结果包装成 Agent 可调用的工具，
 * 让 AI 基于客观图谱数据做决策建议，最终由用户拍板。
 */
@Component
@RequiredArgsConstructor
public class GraphAnalysisTools {

    private final GraphIntelligenceService graphIntelligenceService;
    private final SupplierScoreService supplierScoreService;

    @Tool(description = "分析某物料的供应链集中度风险（赫芬达尔指数 HHI），返回供应商数量、HHI 与风险等级")
    public String analyzeConcentrationRisk(@ToolParam(description = "物料名称") String materialName) {
        List<ConcentrationRiskVO> list = graphIntelligenceService.analyzeConcentration(materialName);
        if (list.isEmpty()) {
            return "未找到物料「" + materialName + "」的供应数据，无法计算集中度风险。";
        }
        StringBuilder sb = new StringBuilder("物料「").append(materialName).append("」供应链集中度风险：\n");
        for (ConcentrationRiskVO vo : list) {
            sb.append("- 供应商数: ").append(vo.getSupplierCount())
                    .append(", HHI: ").append(pct(vo.getHhi()))
                    .append(", 风险等级: ").append(vo.getRiskLevel()).append('\n');
        }
        return sb.toString();
    }

    @Tool(description = "为指定供应商发现可替代供应商（供应相同物料且状态合规），按价格差升序返回")
    public String findAlternativeSuppliers(@ToolParam(description = "目标供应商ID") Long supplierId) {
        List<AlternativeSupplierVO> list = graphIntelligenceService.findAlternativeSuppliers(supplierId);
        if (list.isEmpty()) {
            return "未找到可替代该供应商的合规供应商。";
        }
        StringBuilder sb = new StringBuilder("可替代供应商（按价格优势升序）：\n");
        for (AlternativeSupplierVO vo : list) {
            sb.append("- ").append(vo.getSupplierName())
                    .append("，可替代物料: ").append(joinMaterials(vo.getMaterials()))
                    .append("，价格差: ").append(pct(vo.getPriceDiffAvg()))
                    .append("（正数表示更贵），信用等级: ").append(vo.getCreditLevel()).append('\n');
        }
        return sb.toString();
    }

    @Tool(description = "对指定供应商进行综合评分（价格/质量/交期/服务/风险五维度），返回总分与各维度得分")
    public String scoreSupplier(@ToolParam(description = "供应商ID") Long supplierId) {
        SupplierScoreVO vo = supplierScoreService.score(supplierId);
        if (vo == null || vo.getTotalScore() == null) {
            return "该供应商暂无评分数据。";
        }
        StringBuilder sb = new StringBuilder("供应商「").append(vo.getSupplierName())
                .append("」综合评分: ").append(fmt(vo.getTotalScore())).append(" 分\n各维度得分：\n");
        if (vo.getDimensions() != null) {
            for (SupplierScoreVO.ScoreDimension d : vo.getDimensions()) {
                sb.append("- ").append(d.getName())
                        .append("（权重 ").append(pct(d.getWeight())).append("）: ")
                        .append(fmt(d.getScore())).append(" 分，").append(d.getReason()).append('\n');
            }
        }
        return sb.toString();
    }

    @Tool(description = "构建采购员画像（供应商合作网络、品类覆盖偏好、议价能力），返回结构化画像")
    public String analyzeBuyerProfile(@ToolParam(description = "用户ID") Long userId) {
        BuyerProfileVO vo = graphIntelligenceService.buildBuyerProfile(userId);
        if (vo == null) {
            return "该用户暂无画像数据。";
        }
        String name = vo.getUserName() == null ? String.valueOf(userId) : vo.getUserName();
        StringBuilder sb = new StringBuilder("采购员「").append(name).append("」画像：\n");

        if (vo.getSupplierNetwork() != null && !vo.getSupplierNetwork().isEmpty()) {
            sb.append("【供应商合作网络】\n");
            for (BuyerProfileVO.SupplierNetworkItem item : vo.getSupplierNetwork()) {
                sb.append("- ").append(item.getSupplierName())
                        .append(": ").append(item.getOrderCount()).append(" 单，累计 ")
                        .append(fmt(item.getTotalAmount())).append(" 元\n");
            }
        }

        if (vo.getCategoryCoverage() != null && !vo.getCategoryCoverage().isEmpty()) {
            sb.append("【品类覆盖偏好】\n");
            for (BuyerProfileVO.CategoryItem item : vo.getCategoryCoverage()) {
                sb.append("- ").append(item.getCategory()).append(" / ").append(item.getMaterial())
                        .append(": ").append(item.getCount()).append(" 次\n");
            }
        }

        if (vo.getBargaining() != null && !vo.getBargaining().isEmpty()) {
            sb.append("【议价能力】\n");
            for (BuyerProfileVO.BargainItem item : vo.getBargaining()) {
                sb.append("- ").append(item.getMaterialName())
                        .append(": 我的价 ").append(fmt(item.getMyPrice()))
                        .append(" vs 市场价 ").append(fmt(item.getMarketPrice()))
                        .append("，节约率 ").append(pct(item.getSavingRate())).append('\n');
            }
        }
        return sb.toString();
    }

    private String joinMaterials(List<String> materials) {
        return materials == null || materials.isEmpty() ? "无" : String.join("/", materials);
    }

    private String fmt(Double v) {
        return v == null ? "N/A" : String.format("%.2f", v);
    }

    private String pct(Double v) {
        return v == null ? "N/A" : String.format("%.1f%%", v * 100);
    }
}
