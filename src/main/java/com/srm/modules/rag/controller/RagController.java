package com.srm.modules.rag.controller;

import com.srm.common.Result;
import com.srm.modules.rag.service.HybridRagService;
import com.srm.modules.rag.vo.VectorDocument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 混合检索 RAG 接口。
 */
@Tag(name = "混合检索RAG")
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final HybridRagService hybridRagService;

    @Operation(summary = "混合检索（向量 + 图谱结构化过滤）")
    @GetMapping("/search")
    public Result<List<VectorDocument>> search(@RequestParam String query,
                                               @RequestParam(defaultValue = "5") int topK) {
        return Result.ok(hybridRagService.retrieve(query, topK));
    }
}
