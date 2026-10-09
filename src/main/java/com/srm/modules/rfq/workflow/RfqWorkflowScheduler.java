package com.srm.modules.rfq.workflow;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.srm.modules.rfq.entity.RfqWorkflow;
import com.srm.modules.rfq.enums.RfqStatus;
import com.srm.modules.rfq.mapper.RfqWorkflowMapper;
import com.srm.modules.rfq.service.RfqLockService;
import com.srm.modules.rfq.vo.QuoteRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 询比价工作流调度器。
 * <pre>
 *   ① 报价截止的询比价单自动进入「分析中」；
 *   ② 扫描「分析中」的询比价单，异步执行分析步骤链；
 *   ③ 按询比价单加 Redis 锁（用户级别），防止同一单被重复分析。
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RfqWorkflowScheduler {

    private static final String ANALYZE_LOCK_PREFIX = "srm:rfq:lock:analyze:";
    private static final long ANALYZE_LOCK_TTL_SECONDS = 300;

    private final RfqWorkflowMapper rfqMapper;
    private final RfqStepChain stepChain;
    private final RfqLockService lockService;

    /** 自注入代理，保证 @Async 生效 */
    @Lazy
    @Autowired
    private RfqWorkflowScheduler self;

    /**
     * 触发时机：每 30 秒轮询一次（{@code fixedDelay=30000}）。
     * 触发条件：状态为 QUOTING 且 {@code quote_deadline <= now}（报价已截止）。
     * 动作：置为 ANALYZING，交由 {@link #runPendingAnalysis()} 分析。
     */
    @Scheduled(fixedDelay = 30000)
    public void checkDeadlineAndTrigger() {
        List<RfqWorkflow> expired = rfqMapper.selectList(
                new LambdaQueryWrapper<RfqWorkflow>()
                        .eq(RfqWorkflow::getStatus, RfqStatus.QUOTING.name())
                        .le(RfqWorkflow::getQuoteDeadline, LocalDateTime.now()));
        for (RfqWorkflow rfq : expired) {
            rfq.setStatus(RfqStatus.ANALYZING.name());
            rfqMapper.updateById(rfq);
            log.info("询比价单 {} 报价截止，进入分析", rfq.getRfqNo());
        }
    }

    /**
     * 触发时机：每 30 秒轮询一次（{@code fixedDelay=30000}）。
     * 触发条件：状态为 ANALYZING（由报价截止或全部报价齐进入）。
     * 动作：逐单异步执行 {@link #analyzeAsync(Long)} 分析步骤链。
     */
    @Scheduled(fixedDelay = 30000)
    public void runPendingAnalysis() {
        List<RfqWorkflow> pendings = rfqMapper.selectList(
                new LambdaQueryWrapper<RfqWorkflow>()
                        .eq(RfqWorkflow::getStatus, RfqStatus.ANALYZING.name()));
        for (RfqWorkflow rfq : pendings) {
            self.analyzeAsync(rfq.getId());
        }
    }

    /** 异步执行分析；按询比价单加 Redis 锁，防止同一单被重复分析。 */
    @Async
    public void analyzeAsync(Long rfqId) {
        String lockKey = ANALYZE_LOCK_PREFIX + rfqId;
        if (!lockService.tryLock(lockKey, ANALYZE_LOCK_TTL_SECONDS)) {
            log.info("[RFQ] 询比价单 {} 正在分析中，跳过", rfqId);
            return;
        }
        try {
            doAnalyze(rfqId);
        } finally {
            lockService.unlock(lockKey);
        }
    }

    /** 真正执行分析：无有效报价则流标（EXPIRED），否则执行评分/风险/推荐/报告步骤链。 */
    private void doAnalyze(Long rfqId) {
        RfqWorkflow rfq = rfqMapper.selectById(rfqId);
        if (rfq == null || !RfqStatus.ANALYZING.name().equals(rfq.getStatus())) {
            return;
        }
        List<QuoteRecord> quotes = parseQuotes(rfq.getQuotes());
        if (quotes.isEmpty()) {
            rfq.setStatus(RfqStatus.EXPIRED.name());
            rfqMapper.updateById(rfq);
            log.info("询比价单 {} 无有效报价，已流标", rfq.getRfqNo());
            return;
        }

        RfqContext ctx = new RfqContext(rfq);
        ctx.setQuotes(quotes);
        stepChain.execute(ctx);
    }

    private List<QuoteRecord> parseQuotes(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return JSONUtil.toList(json, QuoteRecord.class);
    }
}
