package com.srm.modules.rag.eval;

import com.srm.config.RabbitMqConfig;
import com.srm.modules.rag.eval.model.EvalReport;
import com.srm.modules.rag.eval.model.EvalTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RAG 评测消费者：从 RabbitMQ 消费评测任务，后台异步执行并回写结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagEvalListener {

    private final RagEvalService ragEvalService;
    private final RagEvalRunner ragEvalRunner;
    private final RagEvalStoreService ragEvalStoreService;

    @RabbitListener(queues = RabbitMqConfig.RAG_EVAL_QUEUE)
    public void onEvalTask(EvalTaskMessage message) {
        log.info("收到评测任务 runId={}, topK={}", message.runId(), message.topK());
        ragEvalStoreService.markRunning(message.runId());
        try {
            EvalReport report = ragEvalRunner.run(ragEvalService.loadGoldenDataset(), message.topK());
            ragEvalStoreService.markCompleted(message.runId(), report);
            log.info("评测任务完成 runId={}, 用例数={}", message.runId(), report.getTotalCases());
        } catch (Exception e) {
            log.error("评测任务失败 runId={}", message.runId(), e);
            ragEvalStoreService.markFailed(message.runId());
        }
    }
}
