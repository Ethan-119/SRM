package com.srm.modules.rag.controller;

import com.srm.common.Result;
import com.srm.modules.rag.dto.RagDocumentDTO;
import com.srm.modules.rag.service.HybridRagService;
import com.srm.modules.rag.service.RagDocumentService;
import com.srm.modules.rag.vo.VectorDocument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    private final RagDocumentService ragDocumentService;

    @Operation(summary = "混合检索（向量 + 图谱结构化过滤）")
    @GetMapping("/search")
    public Result<List<VectorDocument>> search(@RequestParam String query,
                                               @RequestParam(defaultValue = "5") int topK) {
        return Result.ok(hybridRagService.retrieve(query, topK));
    }

    @Operation(summary = "写入/更新一条向量文档")
    @PostMapping("/documents")
    public Result<Void> addDocument(@Valid @RequestBody RagDocumentDTO dto) {
        ragDocumentService.add(dto.getId(), dto.getTitle(), dto.getContent(), dto.getSource(), dto.getMetadata());
        return Result.ok();
    }

    @Operation(summary = "批量写入/更新向量文档")
    @PostMapping("/documents/batch")
    public Result<Integer> addDocuments(@RequestBody List<RagDocumentDTO> docs) {
        int count = 0;
        for (RagDocumentDTO dto : docs) {
            if (dto == null || dto.getId() == null || dto.getId().isBlank()
                    || dto.getContent() == null || dto.getContent().isBlank()) {
                continue;
            }
            ragDocumentService.add(dto.getId(), dto.getTitle(), dto.getContent(), dto.getSource(), dto.getMetadata());
            count++;
        }
        return Result.ok(count);
    }
}
