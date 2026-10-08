package com.srm.modules.rfq.workflow.step;

import com.srm.modules.rfq.entity.RfqWorkflow;
import com.srm.modules.rfq.vo.QuoteRecord;
import com.srm.modules.rfq.vo.RiskCheckResult;
import com.srm.modules.rfq.vo.SupplierScore;
import com.srm.modules.rfq.workflow.RfqContext;
import com.srm.modules.rfq.workflow.RfqStep;
import com.srm.modules.rfq.workflow.StepResult;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 步骤4：AI 生成推荐报告（失败可降级）。
 * 调 LLM 生成推荐理由；失败时降级为默认理由，不中断流程。
 */
@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class ReportStep implements RfqStep {

    private final ChatClient chatClient;
    private final SupplierService supplierService;

    @Override
    public String name() {
        return "AI推荐报告";
    }

    @Override
    public boolean shouldContinueOnFail() {
        return true; // 报告失败可降级为默认理由，不阻断分析
    }

    @Override
    public StepResult execute(RfqContext ctx) {
        try {
            String report = generateReport(ctx);
            ctx.setReport(report);
            return StepResult.success("推荐报告生成完成");
        } catch (Exception e) {
            log.warn("AI 报告生成失败，使用默认推荐理由 rfqId={}", ctx.getRfq().getId(), e);
            ctx.setReport("综合评分最高，风险可控");
            return StepResult.success("报告降级为默认理由");
        }
    }

    private String generateReport(RfqContext ctx) {
        RfqWorkflow rfq = ctx.getRfq();
        Map<Long, Supplier> supplierMap = supplierService.list().stream()
                .collect(Collectors.toMap(Supplier::getId, s -> s, (a, b) -> a));

        StringBuilder prompt = new StringBuilder();
        prompt.append("作为采购决策助手，请基于以下数据生成推荐报告（100字以内）：\n");
        prompt.append("物料: ").append(rfq.getMaterialName())
                .append("，数量: ").append(rfq.getQuantity()).append("\n");
        for (QuoteRecord q : ctx.getQuotes()) {
            SupplierScore score = ctx.getScores().stream()
                    .filter(s -> s.getSupplierId().equals(q.getSupplierId())).findFirst().orElse(null);
            RiskCheckResult risk = ctx.getRisks().stream()
                    .filter(r -> r.getSupplierId().equals(q.getSupplierId())).findFirst().orElse(null);
            Supplier sp = supplierMap.get(q.getSupplierId());
            String name = sp == null ? "供应商#" + q.getSupplierId() : sp.getSupplierName();
            prompt.append("- ").append(name)
                    .append(": 单价¥").append(q.getPrice()).append("，交期").append(q.getDeliveryDays())
                    .append("天，评分").append(score == null ? 0 : score.getTotalScore())
                    .append("，风险:").append(risk == null || risk.getWarnings().isEmpty()
                            ? "无" : String.join("；", risk.getWarnings()))
                    .append("\n");
        }
        prompt.append("推荐供应商: ").append(supplierName(ctx.getRecommendSupplierId()))
                .append("。请说明推荐理由（含价格/交期/风险权衡）。");

        return chatClient.prompt().user(prompt.toString()).call().content();
    }

    private String supplierName(Long supplierId) {
        if (supplierId == null) {
            return "未知";
        }
        Supplier s = supplierService.getById(supplierId);
        return s == null ? "供应商#" + supplierId : s.getSupplierName();
    }
}
