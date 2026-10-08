package com.srm.modules.rfq.workflow.step;

import com.srm.modules.rfq.service.SupplierScoringService;
import com.srm.modules.rfq.vo.QuoteRecord;
import com.srm.modules.rfq.vo.SupplierScore;
import com.srm.modules.rfq.workflow.RfqContext;
import com.srm.modules.rfq.workflow.RfqStep;
import com.srm.modules.rfq.workflow.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 步骤1：综合评分（失败中断）。
 * 价格/交期以所有报价中的最优值为基准归一化，交给 {@link SupplierScoringService} 计算。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class ScoreStep implements RfqStep {

    private final SupplierScoringService scoringService;

    @Override
    public String name() {
        return "综合评分";
    }

    @Override
    public StepResult execute(RfqContext ctx) {
        List<QuoteRecord> quotes = ctx.getQuotes();
        if (quotes == null || quotes.isEmpty()) {
            return StepResult.fail("无有效报价");
        }
        BigDecimal minPrice = quotes.stream().map(QuoteRecord::getPrice)
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        int minDelivery = quotes.stream().mapToInt(QuoteRecord::getDeliveryDays).min().orElse(1);
        List<SupplierScore> scores = quotes.stream()
                .map(q -> scoringService.calculate(q.getSupplierId(), q.getPrice(),
                        q.getDeliveryDays(), minPrice, minDelivery))
                .toList();
        ctx.setScores(scores);
        return StepResult.success("评分完成，共 " + scores.size() + " 家");
    }
}
