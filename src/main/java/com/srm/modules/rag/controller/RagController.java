package com.srm.modules.rag.controller;

import com.srm.common.Result;
import com.srm.config.RabbitMqConfig;
import com.srm.modules.rag.dto.RagDocumentDTO;
import com.srm.modules.rag.service.DocumentParserService;
import com.srm.modules.rag.service.HybridRagService;
import com.srm.modules.rag.service.TextChunker;
import com.srm.modules.rag.vo.FileUploadResult;
import com.srm.modules.rag.vo.VectorDocument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 混合检索 RAG 接口。
 */
@Slf4j
@Tag(name = "混合检索RAG")
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final HybridRagService hybridRagService;
    private final RabbitTemplate rabbitTemplate;
    private final DocumentParserService documentParserService;
    private final TextChunker textChunker;

    @Operation(summary = "混合检索（向量 + 图谱结构化过滤）")
    @GetMapping("/search")
    public Result<List<VectorDocument>> search(@RequestParam String query,
                                               @RequestParam(defaultValue = "5") int topK) {
        return Result.ok(hybridRagService.retrieve(query, topK));
    }

    @Operation(summary = "提交文档向量化任务（异步）")
    @PostMapping("/documents")
    public Result<Void> addDocument(@Valid @RequestBody RagDocumentDTO dto) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.RAG_DOCUMENT_QUEUE, dto);
        return Result.ok();
    }

    @Operation(summary = "批量提交文档向量化任务（异步）")
    @PostMapping("/documents/batch")
    public Result<Integer> addDocuments(@RequestBody List<RagDocumentDTO> docs) {
        int count = 0;
        for (RagDocumentDTO dto : docs) {
            if (dto == null || dto.getId() == null || dto.getId().isBlank()
                    || dto.getContent() == null || dto.getContent().isBlank()) {
                continue;
            }
            rabbitTemplate.convertAndSend(RabbitMqConfig.RAG_DOCUMENT_QUEUE, dto);
            count++;
        }
        return Result.ok(count);
    }

    @Operation(summary = "上传文件（PDF/Word/CSV/TXT/MD）解析并异步向量化")
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<FileUploadResult>> uploadDocuments(
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        List<FileUploadResult> results = new ArrayList<>();
        if (files == null || files.isEmpty()) {
            return Result.ok(results);
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String name = file.getOriginalFilename();
            try {
                String text = documentParserService.extractText(file);
                if (text == null || text.isBlank()) {
                    results.add(new FileUploadResult(name, 0, "未解析出文本内容"));
                    continue;
                }
                List<String> chunks = textChunker.chunk(text);
                String baseId = baseName(name) + "-" + UUID.randomUUID().toString().substring(0, 8);
                for (int i = 0; i < chunks.size(); i++) {
                    RagDocumentDTO dto = new RagDocumentDTO();
                    dto.setId(baseId + "-chunk-" + i);
                    dto.setTitle(name);
                    dto.setContent(chunks.get(i));
                    dto.setSource("file:" + name);
                    dto.setMetadata(Map.of(
                            "fileName", name,
                            "fileType", extension(name),
                            "chunkIndex", i,
                            "totalChunks", chunks.size()));
                    rabbitTemplate.convertAndSend(RabbitMqConfig.RAG_DOCUMENT_QUEUE, dto);
                }
                results.add(new FileUploadResult(name, chunks.size(), "已提交向量化"));
            } catch (Exception e) {
                log.warn("文件解析失败: {}", name, e);
                results.add(new FileUploadResult(name, 0, "解析失败: " + e.getMessage()));
            }
        }
        return Result.ok(results);
    }

    private String baseName(String name) {
        if (name == null) {
            return "doc";
        }
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(0, i) : name;
    }

    private String extension(String name) {
        if (name == null) {
            return "";
        }
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1).toLowerCase() : "";
    }
}
