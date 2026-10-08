package com.srm.modules.rag.service;

import com.srm.modules.rag.vo.VectorHit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * pgvector 底层向量服务：只负责「文本 -> 向量」「存向量(doc_id+embedding)」「相似度检索(doc_id+相似度)」。
 * 文档正文在 rag_document 主数据表，由 RagDocumentService 编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PgVectorStoreService {

    private final JdbcTemplate pgVectorJdbcTemplate;
    private final EmbeddingModel embeddingModel;

    @Value("${srm.pgvector.table:vector_store}")
    private String table;

    /** 文本 -> 向量 */
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    /** 保存（或更新）文档向量：doc_id + embedding。 */
    public void saveVector(String docId, String content) {
        float[] vec = embed(content);
        String vector = toVectorLiteral(vec);
        log.info("[pgvector] 保存向量: doc_id={}, 内容长度={} 字符, 向量维度={}", docId, content.length(), vec.length);
        String sql = "INSERT INTO " + table + " (doc_id, embedding) VALUES (?, ?::vector) " +
                "ON CONFLICT (doc_id) DO UPDATE SET embedding = EXCLUDED.embedding";
        pgVectorJdbcTemplate.update(sql, docId, vector);
    }

    /** 余弦相似度检索，返回命中的 doc_id 与相似度。 */
    public List<VectorHit> search(String query, int topK) {
        float[] vec = embed(query);
        String vector = toVectorLiteral(vec);
        log.info("[pgvector] 语义检索: query={}, topK={}, 向量维度={}", query, topK, vec.length);
        String sql = "SELECT doc_id, 1 - (embedding <=> ?::vector) AS similarity " +
                "FROM " + table + " ORDER BY embedding <=> ?::vector LIMIT ?";
        List<VectorHit> hits = pgVectorJdbcTemplate.query(sql, ps -> {
            ps.setString(1, vector);
            ps.setString(2, vector);
            ps.setInt(3, topK);
        }, (rs, rowNum) -> new VectorHit(
                rs.getString("doc_id"),
                rs.getDouble("similarity")));
        log.info("[pgvector] 检索命中 {} 条", hits.size());
        return hits;
    }

    private String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
