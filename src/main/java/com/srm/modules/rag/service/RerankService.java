package com.srm.modules.rag.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.srm.modules.rag.vo.VectorDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 交叉编码器（cross-encoder）重排序：调用 DashScope qwen3.7-text-rerank。
 *
 * <p>与向量召回的 bi-encoder 不同，rerank 把「query + 文档」拼成一对逐条过模型，
 * 输出细粒度相关性分数（0~1），比「余弦相似度 + 规则加减分」更准。代价是慢、贵，
 * 因此只对向量召回后的少量候选做精排。</p>
 *
 * <p>请求走 DashScope 原生接口（嵌套 input/parameters 结构），响应按 relevance_score 降序。
 * 调用失败时降级为「原候选顺序截断」，保证检索可用性优先。</p>
 */
@Slf4j
@Service
public class RerankService {

    @Value("${srm.rerank.model:qwen3.7-text-rerank}")
    private String model;

    @Value("${srm.rerank.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    /**
     * 对候选文档重排序，返回按相关性降序的精排结果（最多 topK 条）。
     */
    public List<VectorDocument> rerank(String query, List<VectorDocument> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<String> docs = candidates.stream().map(VectorDocument::content).toList();

        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "input", Map.of("query", query == null ? "" : query, "documents", docs),
                    "parameters", Map.of("top_n", Math.min(topK, docs.size()))
            );

            String resp = HttpRequest.post(baseUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(JSONUtil.toJsonStr(body))
                    .timeout(30_000)
                    .execute()
                    .body();

            JSONObject json = JSONUtil.parseObj(resp);
            JSONArray results = json.getJSONObject("output").getJSONArray("results");

            List<VectorDocument> ranked = new ArrayList<>(results.size());
            for (int i = 0; i < results.size(); i++) {
                JSONObject r = results.getJSONObject(i);
                int idx = r.getInt("index");
                double score = r.getDouble("relevance_score");
                VectorDocument d = candidates.get(idx);
                // 复用 similarity 字段承载 rerank 分数（同为 0~1、越大越相关）
                ranked.add(new VectorDocument(d.id(), d.content(), d.metadata(), score));
            }
            return ranked;
        } catch (Exception e) {
            log.warn("[rerank] 调用失败，降级为向量召回顺序 query={}", query, e);
            int limit = Math.min(topK, candidates.size());
            return new ArrayList<>(candidates.subList(0, limit));
        }
    }
}
