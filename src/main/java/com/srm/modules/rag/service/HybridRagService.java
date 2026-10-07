package com.srm.modules.rag.service;

import com.srm.modules.graph.repository.GraphRepository;
import com.srm.modules.rag.vo.VectorDocument;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Record;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 混合检索 RAG：向量检索（pgvector）+ 结构化过滤（Neo4j）。
 *
 * 流程：
 * 1. 向量检索 Top-20（语义相似）
 * 2. 提取文档中提到的供应商名称
 * 3. Neo4j 结构化过滤：只保留「已准入 / 合作中」且无过期资质的供应商
 * 4. 过滤 + 重排序，返回 Top-K
 */
@Service
@RequiredArgsConstructor
public class HybridRagService {

    private final PgVectorStoreService pgVectorStoreService;
    private final GraphRepository graphRepository;

    @Value("${srm.pgvector.top-k:20}")
    private int vectorTopK;

    /** 命中合规供应商时的加权 */
    private static final double VALID_BOOST = 0.20;
    /** 命中被过滤供应商时的惩罚 */
    private static final double INVALID_PENALTY = 0.20;

    public List<VectorDocument> retrieve(String query, int topK) {
        // 1. 向量检索 Top-N
        List<VectorDocument> candidates = pgVectorStoreService.search(query, vectorTopK);
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 2. 提取候选文档中提到的供应商名称
        Set<String> mentionedNames = extractMentionedSupplierNames(candidates);
        if (mentionedNames.isEmpty()) {
            return rerankAndLimit(candidates, Set.of(), Set.of(), topK);
        }

        // 3. Neo4j 结构化过滤
        Set<String> validNames = filterValidActiveSuppliers(mentionedNames);
        Set<String> invalidNames = new HashSet<>(mentionedNames);
        invalidNames.removeAll(validNames);

        // 4. 重排序
        return rerankAndLimit(candidates, validNames, invalidNames, topK);
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

    private List<VectorDocument> rerankAndLimit(List<VectorDocument> candidates,
                                                Set<String> validNames,
                                                Set<String> invalidNames,
                                                int topK) {
        List<VectorDocument> ranked = new ArrayList<>(candidates.size());
        for (VectorDocument doc : candidates) {
            double score = doc.similarity();
            if (containsAny(doc.content(), validNames)) {
                score += VALID_BOOST;
            }
            if (containsAny(doc.content(), invalidNames)) {
                score -= INVALID_PENALTY;
            }
            ranked.add(new VectorDocument(doc.id(), doc.content(), doc.metadata(), score));
        }
        ranked.sort(Comparator.comparingDouble(VectorDocument::similarity).reversed());
        int limit = Math.min(topK, ranked.size());
        return ranked.subList(0, limit);
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
