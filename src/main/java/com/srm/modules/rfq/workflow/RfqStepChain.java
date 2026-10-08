package com.srm.modules.rfq.workflow;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 询比价分析步骤链执行器。
 *
 * <p>Spring 启动时自动收集所有 {@link RfqStep} 实现，按 {@code @Order} 排序。
 * 执行语义：
 * <ul>
 *   <li>步骤成功 → 继续下一步；</li>
 *   <li>步骤失败且 {@code shouldContinueOnFail()=true} → 打降级日志后继续；</li>
 *   <li>步骤失败且 {@code shouldContinueOnFail()=false} → 后续「工作步骤」跳过，
 *       仅「记录步骤」（{@code isRecorder()=true}）继续执行；</li>
 * </ul>
 * 新增步骤：实现 {@link RfqStep} + 标注 {@code @Component/@Order(N)} 即可，无需改动本类。
 */
@Slf4j
@Component
public class RfqStepChain {

    private final List<RfqStep> steps;

    public RfqStepChain(@Autowired(required = false) List<RfqStep> steps) {
        this.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
    }

    @PostConstruct
    public void init() {
        steps.sort(AnnotationAwareOrderComparator.INSTANCE);
        log.info("[RFQ] 步骤链注册完成，共 {} 步: {}",
                steps.size(),
                steps.stream().map(RfqStep::name).reduce((a, b) -> a + " -> " + b).orElse(""));
    }

    /** 按序执行整条步骤链。 */
    public void execute(RfqContext ctx) {
        Long rfqId = ctx.getRfq().getId();
        for (RfqStep step : steps) {
            if (ctx.isStopped()) {
                if (step.isRecorder()) {
                    log.info("[RFQ] 步骤[{}] rfqId={}：前面步骤已失败，记录步骤继续执行", step.name(), rfqId);
                    doRun(step, ctx);
                } else {
                    log.info("[RFQ] 步骤[{}] rfqId={}：前面步骤已失败，跳过", step.name(), rfqId);
                }
                continue;
            }
            doRun(step, ctx);
        }
    }

    private void doRun(RfqStep step, RfqContext ctx) {
        Long rfqId = ctx.getRfq().getId();
        log.info("[RFQ] 步骤[{}] 开始 rfqId={}", step.name(), rfqId);
        try {
            StepResult result = step.execute(ctx);
            if (result.isSuccess()) {
                log.info("[RFQ] 步骤[{}] 成功 rfqId={}：{}", step.name(), rfqId, result.getMessage());
                return;
            }
            ctx.getErrors().add(result.getMessage());
            ctx.setErrorStep(step.name());
            if (step.shouldContinueOnFail()) {
                log.warn("[RFQ] 步骤[{}] 失败但可降级继续 rfqId={}：{}", step.name(), rfqId, result.getMessage());
            } else {
                log.warn("[RFQ] 步骤[{}] 失败，后续工作步骤中断 rfqId={}：{}", step.name(), rfqId, result.getMessage());
                ctx.setStopped(true);
            }
        } catch (Exception e) {
            ctx.getErrors().add(step.name() + "异常：" + e.getMessage());
            ctx.setErrorStep(step.name());
            if (step.shouldContinueOnFail()) {
                log.warn("[RFQ] 步骤[{}] 异常但可降级继续 rfqId={}", step.name(), rfqId, e);
            } else {
                log.warn("[RFQ] 步骤[{}] 异常，后续工作步骤中断 rfqId={}", step.name(), rfqId, e);
                ctx.setStopped(true);
            }
        }
    }
}
