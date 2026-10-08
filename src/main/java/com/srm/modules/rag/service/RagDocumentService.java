package com.srm.modules.rag.service;

import cn.hutool.json.JSONUtil;
import com.srm.modules.rag.entity.RagDocument;
import com.srm.modules.rag.mapper.RagDocumentMapper;
import com.srm.modules.rag.vo.VectorDocument;
import com.srm.modules.rag.vo.VectorHit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RAG 文档编排服务：文档主数据（rag_document）+ 向量索引（pgvector）。
 */
@Service
@RequiredArgsConstructor
public class RagDocumentService {

    private final RagDocumentMapper ragDocumentMapper;
    private final PgVectorStoreService pgVectorStoreService;

    /** 写入/更新：先落主数据，再写向量索引。 */
    public void add(String id, String title, String content, String source, Map<String, Object> metadata) {
        RagDocument doc = new RagDocument();
        doc.setId(id);
        doc.setTitle(title);
        doc.setContent(content);
        doc.setSource(source);
        doc.setMetadata(metadata == null ? null : JSONUtil.toJsonStr(metadata));

        if (ragDocumentMapper.selectById(id) != null) {
            ragDocumentMapper.updateById(doc);
        } else {
            ragDocumentMapper.insert(doc);
        }
        pgVectorStoreService.saveVector(id, content);
    }

    /** 语义检索：向量命中后回查主数据正文。 */
    public List<VectorDocument> search(String query, int topK) {
        List<VectorHit> hits = pgVectorStoreService.search(query, topK);
        if (hits.isEmpty()) {
            return List.of();
        }
        List<String> ids = hits.stream().map(VectorHit::docId).toList();
        Map<String, RagDocument> docMap = ragDocumentMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(RagDocument::getId, Function.identity()));

        List<VectorDocument> result = new ArrayList<>();
        for (VectorHit hit : hits) {
            RagDocument doc = docMap.get(hit.docId());
            if (doc == null) {
                continue; // 向量残留但主数据已删
            }
            result.add(new VectorDocument(doc.getId(), doc.getContent(), doc.getMetadata(), hit.similarity()));
        }
        return result;
    }
}
