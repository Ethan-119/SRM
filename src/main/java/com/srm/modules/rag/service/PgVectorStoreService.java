package com.srm.modules.rag.service;

import cn.hutool.json.JSONUtil;
import com.srm.modules.rag.vo.VectorDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * pgvector 向量存取服务。
 * 负责：文本 -> 向量（EmbeddingModel）-> pgvector 存储 / 余弦相似度检索。
 */
@Service
@RequiredArgsConstructor
public class PgVectorStoreService {

    private final JdbcTemplate pgVectorJdbcTemplate;
    private final EmbeddingModel embeddingModel;

    @Value("${srm.pgvector.table:vector_store}")
    private String table;

    @Value("${srm.pgvector.dimension:1024}")
    private int dimension;

    /**
     * 余弦相似度检索，返回 Top-K 文档片段。
     */
    public List<VectorDocument> search(String query, int topK) {
        String vector = toVectorLiteral(embeddingModel.embed(query));
        String sql = "SELECT id, content, metadata, 1 - (embedding <=> ?::vector) AS similarity " +
                "FROM " + table + " " +
                "ORDER BY embedding <=> ?::vector LIMIT ?";
        return pgVectorJdbcTemplate.query(sql, ps -> {
            ps.setString(1, vector);
            ps.setString(2, vector);
            ps.setInt(3, topK);
        }, (rs, rowNum) -> new VectorDocument(
                rs.getString("id"),
                rs.getString("content"),
                rs.getString("metadata"),
                rs.getDouble("similarity")
        ));
    }

    /**
     * 写入（或更新）一条文档片段。
     */
    public void add(String id, String content, Map<String, Object> metadata) {
        String vector = toVectorLiteral(embeddingModel.embed(content));
        String metadataJson = metadata == null ? "{}" : JSONUtil.toJsonStr(metadata);
        String sql = "INSERT INTO " + table + " (id, content, metadata, embedding) " +
                "VALUES (?, ?, ?::jsonb, ?::vector) " +
                "ON CONFLICT (id) DO UPDATE SET " +
                "content = EXCLUDED.content, metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding";
        pgVectorJdbcTemplate.update(sql, id, content, metadataJson, vector);
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
