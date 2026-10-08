package com.srm.modules.rag.controller;

import com.srm.common.Result;
import com.srm.config.RabbitMqConfig;
import com.srm.modules.rag.entity.RagEvalRun;
import com.srm.modules.rag.eval.RagEvalStoreService;
import com.srm.modules.rag.eval.model.EvalTaskMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RAG 评测接口：提交异步评测任务并查询结果。
 */
@Tag(name = "RAG评测")
@RestController
@RequestMapping("/api/rag/eval")
@RequiredArgsConstructor
public class RagEvalController {

    private final RagEvalStoreService ragEvalStoreService;
    private final RabbitTemplate rabbitTemplate;

    @Operation(summary = "提交评测任务（异步）")
    @PostMapping("/run")
    public Result<Long> run(@RequestParam(defaultValue = "5") int k) {
        Long runId = ragEvalStoreService.createPendingRun("golden-dataset.yml", k);
        rabbitTemplate.convertAndSend(RabbitMqConfig.RAG_EVAL_QUEUE, new EvalTaskMessage(runId, k));
        return Result.ok(runId);
    }

    @Operation(summary = "查询评测批次列表")
    @GetMapping("/runs")
    public Result<List<RagEvalRun>> runs() {
        return Result.ok(ragEvalStoreService.listRuns());
    }
}
