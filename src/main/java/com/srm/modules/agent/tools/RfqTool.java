package com.srm.modules.agent.tools;

import cn.hutool.json.JSONUtil;
import com.srm.modules.rfq.entity.RfqWorkflow;
import com.srm.modules.rfq.service.RfqWorkflowService;
import com.srm.modules.rfq.vo.AiAnalysisResult;
import com.srm.modules.rfq.vo.QuoteRecord;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 询比价深度分析工具。
 *
 * <p>把「正式询价」从独立工作流收敛为 Agent 的一个工具：创建询比价单、模拟收报价、
 * 完成评分/风险/推荐分析并留痕，最后把推荐结果回传给对话。用户需要正式询价/定标时调用。</p>
 */
@Component
@RequiredArgsConstructor
public class RfqTool {

    private final RfqWorkflowService rfqService;
    private final SupplierService supplierService;

    @Tool(description = "对指定物料发起正式询比价：创建询比价单、收集供应商报价、完成 AI 评分/风险/推荐分析，返回推荐供应商与理由")
    public String runFormalRfq(@ToolParam(description = "物料名称") String materialName,
                               @ToolParam(description = "采购数量") BigDecimal quantity,
                               @ToolParam(description = "参与询价的供应商ID列表，可为空（默认所有合作中供应商）", required = false) List<Long> supplierIds) {
        RfqWorkflow rfq = rfqService.createAndAnalyze(materialName, quantity, supplierIds);
        AiAnalysisResult analysis = JSONUtil.toBean(rfq.getAiAnalysis(), AiAnalysisResult.class);
        List<QuoteRecord> quotes = JSONUtil.toList(rfq.getQuotes(), QuoteRecord.class);

        Map<Long, Supplier> supplierMap = supplierService.list().stream()
                .collect(Collectors.toMap(Supplier::getId, s -> s, (a, b) -> a));

        StringBuilder sb = new StringBuilder();
        sb.append("正式询比价分析完成（单号 ").append(rfq.getRfqNo()).append("）：\n");
        sb.append("物料: ").append(rfq.getMaterialName())
                .append("，数量: ").append(rfq.getQuantity()).append("\n");
        sb.append("各供应商报价与评分：\n");
        for (QuoteRecord q : quotes) {
            Supplier sp = supplierMap.get(q.getSupplierId());
            String name = sp == null ? "供应商#" + q.getSupplierId() : sp.getSupplierName();
            Integer score = analysis.getTotalScores() == null ? null
                    : analysis.getTotalScores().get(String.valueOf(q.getSupplierId()));
            List<String> warnings = analysis.getRiskWarnings() == null ? null
                    : analysis.getRiskWarnings().get(String.valueOf(q.getSupplierId()));
            sb.append("- ").append(name)
                    .append("：报价 ¥").append(q.getPrice())
                    .append("，交期 ").append(q.getDeliveryDays()).append(" 天")
                    .append("，评分 ").append(score == null ? "N/A" : score)
                    .append("，风险 ").append(warnings == null || warnings.isEmpty()
                            ? "无" : String.join("；", warnings))
                    .append("\n");
        }
        Long recommendId = analysis.getRecommendSupplierId();
        Supplier recommendSp = recommendId == null ? null : supplierMap.get(recommendId);
        sb.append("推荐供应商: ").append(recommendSp == null ? "无" : recommendSp.getSupplierName()).append("\n");
        sb.append("推荐理由: ").append(analysis.getRecommendReason());
        return sb.toString();
    }
}
