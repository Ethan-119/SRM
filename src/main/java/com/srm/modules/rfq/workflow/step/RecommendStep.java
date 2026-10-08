package com.srm.modules.rfq.workflow.step;

import com.srm.modules.rfq.vo.RiskCheckResult;
import com.srm.modules.rfq.vo.SupplierScore;
import com.srm.modules.rfq.workflow.RfqContext;
import com.srm.modules.rfq.workflow.RfqStep;
import com.srm.modules.rfq.workflow.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 步骤3：推荐供应商（失败中断）。
 * 过滤高风险供应商后，取综合评分最高者。
 */
@Slf4j
@Component
@Order(3)
public class RecommendStep implements RfqStep {

    @Override
    public String name() {
        return "推荐供应商";
    }

    @Override
    public StepResult execute(RfqContext ctx) {
        Set<Long> highRisk = ctx.getRisks().stream()
                .filter(r -> "HIGH".equals(r.getRiskLevel()))
                .map(RiskCheckResult::getSupplierId)
                .collect(Collectors.toSet());
        Long recommend = ctx.getScores().stream()
                .filter(s -> !highRisk.contains(s.getSupplierId()))
                .max(Comparator.comparing(SupplierScore::getTotalScore))
                .map(SupplierScore::getSupplierId)
                .orElse(null);
        ctx.setRecommendSupplierId(recommend);
        return StepResult.success(recommend == null ? "无推荐供应商" : "推荐供应商已确定");
    }
}
