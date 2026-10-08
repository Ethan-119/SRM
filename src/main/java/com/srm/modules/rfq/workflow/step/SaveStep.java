package com.srm.modules.rfq.workflow.step;

import cn.hutool.json.JSONUtil;
import com.srm.modules.notification.service.NotificationService;
import com.srm.modules.rfq.entity.RfqWorkflow;
import com.srm.modules.rfq.enums.RfqStatus;
import com.srm.modules.rfq.mapper.RfqWorkflowMapper;
import com.srm.modules.rfq.vo.AiAnalysisResult;
import com.srm.modules.rfq.vo.RiskCheckResult;
import com.srm.modules.rfq.vo.SupplierScore;
import com.srm.modules.rfq.workflow.RfqContext;
import com.srm.modules.rfq.workflow.RfqStep;
import com.srm.modules.rfq.workflow.StepResult;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 步骤5：保存分析结果并通知采购员（失败中断）。
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class SaveStep implements RfqStep {

    private final RfqWorkflowMapper rfqMapper;
    private final NotificationService notificationService;
    private final SupplierService supplierService;

    @Override
    public String name() {
        return "保存分析结果";
    }

    @Override
    public StepResult execute(RfqContext ctx) {
        RfqWorkflow rfq = ctx.getRfq();

        AiAnalysisResult analysis = new AiAnalysisResult();
        analysis.setRecommendSupplierId(ctx.getRecommendSupplierId());
        analysis.setRecommendReason(ctx.getReport());
        analysis.setRiskWarnings(ctx.getRisks().stream().filter(r -> !r.getWarnings().isEmpty())
                .collect(Collectors.toMap(r -> String.valueOf(r.getSupplierId()), RiskCheckResult::getWarnings)));
        analysis.setTotalScores(ctx.getScores().stream()
                .collect(Collectors.toMap(s -> String.valueOf(s.getSupplierId()), SupplierScore::getTotalScore)));

        rfq.setAiAnalysis(JSONUtil.toJsonStr(analysis));
        rfq.setStatus(RfqStatus.PENDING_CONFIRM.name());
        rfqMapper.updateById(rfq);

        notificationService.notifyManager("询比价单 " + rfq.getRfqNo()
                + " 分析完成，推荐供应商 " + supplierName(ctx.getRecommendSupplierId()) + "，请确认。");
        log.info("询比价单 {} 分析完成，推荐供应商 {}", rfq.getRfqNo(), ctx.getRecommendSupplierId());
        return StepResult.success("分析结果已保存");
    }

    private String supplierName(Long supplierId) {
        if (supplierId == null) {
            return "未知";
        }
        Supplier s = supplierService.getById(supplierId);
        return s == null ? "供应商#" + supplierId : s.getSupplierName();
    }
}
