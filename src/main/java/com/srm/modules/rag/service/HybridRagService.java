package com.srm.modules.rag.service;

import com.srm.modules.graph.repository.GraphRepository;
import com.srm.modules.rag.vo.VectorDocument;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Record;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 混合检索 RAG：向量检索（pgvector）+ 结构化过滤（Neo4j）+ 交叉编码器重排序（qwen3.7-text-rerank）。
 *
 * 流程：
 * 1. 向量检索 Top-N（语义召回，放大候选池供 rerank 精排）
 * 2. 提取文档中提到的供应商名称
 * 3. Neo4j 结构化过滤：剔除提到「不合规供应商」的文档（仅保留已准入/合作中且无过期资质）
 * 4. rerank 模型对候选精排，返回 Top-K
 */
@Service
@RequiredArgsConstructor
public class HybridRagService {

    private final RagDocumentService ragDocumentService;
    private final GraphRepository graphRepository;
    private final RerankService rerankService;

    /** 向量召回候选数：放大到 50，供 rerank 精排选择 */
    @Value("${srm.pgvector.top-k:50}")
    private int vectorTopK;

    public List<VectorDocument> retrieve(String query, int topK) {
        // 1. 向量检索 Top-N
        List<VectorDocument> candidates = ragDocumentService.search(query, vectorTopK);
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 2. 提取候选文档中提到的供应商名称
        Set<String> mentionedNames = extractMentionedSupplierNames(candidates);
        List<VectorDocument> compliant = candidates;
        if (!mentionedNames.isEmpty()) {
            Set<String> validNames = filterValidActiveSuppliers(mentionedNames);
            Set<String> invalidNames = new HashSet<>(mentionedNames);
            invalidNames.removeAll(validNames);
            // 3. 结构化硬过滤：剔除提到不合规供应商的文档（过滤后为空则回退全部）
            List<VectorDocument> filtered = candidates.stream()
                    .filter(d -> !containsAny(d.content(), invalidNames))
                    .toList();
            if (!filtered.isEmpty()) {
                compliant = filtered;
            }
        }

        // 4. 交叉编码器重排序
        return rerankService.rerank(query, compliant, topK);
    }

    /** 提取文档内容中出现的所有供应商名称。 */
    private Set<String> extractMentionedSupplierNames(List<VectorDocument> docs) {
        List<Record> records = graphRepository.query("MATCH (s:Supplier) RETURN s.name AS name");
        Set<String> mentioned = new HashSet<>();
        for (Record record : records) {
            String name = record.get("name").asString();
            for (VectorDocument doc : docs) {
                if (doc.content() != null && doc.content().contains(name)) {
                    mentioned.add(name);
                    break;
                }
            }
        }
        return mentioned;
    }

    /** 只保留「已准入 / 合作中」且无过期资质的供应商。 */
    private Set<String> filterValidActiveSuppliers(Set<String> names) {
        String cypher = """
                MATCH (s:Supplier)
                WHERE s.name IN $names AND s.status IN ['已准入', '合作中']
                OPTIONAL MATCH (s)-[:CERTIFIED_WITH]->(c:Cert)
                WITH s, collect(c.status) AS certStatuses
                WHERE none(st IN certStatuses WHERE st = '过期')
                RETURN s.name AS name
                """;
        List<Record> records = graphRepository.query(cypher, Map.of("names", new ArrayList<>(names)));
        Set<String> valid = new HashSet<>();
        for (Record record : records) {
            valid.add(record.get("name").asString());
        }
        return valid;
    }

    private boolean containsAny(String content, Collection<String> names) {
        if (content == null || names.isEmpty()) {
            return false;
        }
        for (String name : names) {
            if (content.contains(name)) {
                return true;
            }
        }
        return false;
    }
}
