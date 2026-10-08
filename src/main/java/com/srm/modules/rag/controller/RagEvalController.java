package com.srm.modules.rag.controller;

import com.srm.common.Result;
import com.srm.modules.rag.entity.RagEvalRun;
import com.srm.modules.rag.eval.RagEvalRunner;
import com.srm.modules.rag.eval.RagEvalService;
import com.srm.modules.rag.eval.RagEvalStoreService;
import com.srm.modules.rag.eval.model.EvalReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RAG 评测接口：运行评测并持久化结果。
 */
@Tag(name = "RAG评测")
@RestController
@RequestMapping("/api/rag/eval")
@RequiredArgsConstructor
public class RagEvalController {

    private final RagEvalService ragEvalService;
    private final RagEvalRunner ragEvalRunner;
    private final RagEvalStoreService ragEvalStoreService;

    @Operation(summary = "运行评测并持久化")
    @PostMapping("/run")
    public Result<EvalReport> run(@RequestParam(defaultValue = "5") int k) {
        EvalReport report = ragEvalRunner.run(ragEvalService.loadGoldenDataset(), k);
        ragEvalStoreService.save(report, "golden-dataset.yml", k);
        return Result.ok(report);
    }

    @Operation(summary = "查询评测批次列表")
    @GetMapping("/runs")
    public Result<List<RagEvalRun>> runs() {
        return Result.ok(ragEvalStoreService.listRuns());
    }
}
