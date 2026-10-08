package com.srm.modules.rfq.workflow.step;

import com.srm.modules.rfq.entity.RfqWorkflow;
import com.srm.modules.rfq.workflow.RfqContext;
import com.srm.modules.rfq.workflow.RfqStep;
import com.srm.modules.rfq.workflow.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 步骤6：记录日志（永不中断，标记为记录步骤）。
 * 前面任一工作步骤硬失败后，本步骤仍会执行并输出一条失败汇总日志。
 */
@Slf4j
@Component
@Order(6)
public class LogStep implements RfqStep {

    @Override
    public String name() {
        return "记录日志";
    }

    @Override
    public boolean shouldContinueOnFail() {
        return true;
    }

    @Override
    public boolean isRecorder() {
        return true;
    }

    @Override
    public StepResult execute(RfqContext ctx) {
        RfqWorkflow rfq = ctx.getRfq();
        if (ctx.getErrors().isEmpty()) {
            log.info("[RFQ] 询比价单 {} 分析成功，推荐供应商 {}", rfq.getRfqNo(), ctx.getRecommendSupplierId());
        } else {
            log.warn("[RFQ] 询比价单 {} 分析失败，错误步骤={}，错误: {}",
                    rfq.getRfqNo(), ctx.getErrorStep(), String.join("；", ctx.getErrors()));
        }
        return StepResult.success("日志记录完成");
    }
}
