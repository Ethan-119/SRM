package com.srm.modules.rag.eval.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * RAG 评估聚合报告。
 */
@Data
public class EvalReport {

    /** 用例总数 */
    private int totalCases;

    /** 平均 Recall@K */
    private Double avgRecallAtK;

    /** 平均 Precision@K */
    private Double avgPrecisionAtK;

    /** MRR（平均倒数排名） */
    private Double mrr;

    /** 平均 NDCG@K */
    private Double avgNdcgAtK;

    /** 检索延迟 P50（毫秒） */
    private Double latencyP50;

    /** 检索延迟 P95（毫秒） */
    private Double latencyP95;

    /** 检索延迟 P99（毫秒） */
    private Double latencyP99;

    /** 平均忠实度（可空） */
    private Double avgFaithfulness;

    /** 按标签分组统计 */
    private Map<String, TagStats> byTag;

    /** 明细结果 */
    private List<EvalResult> results;

    @Data
    public static class TagStats {
        private int count;
        private Double avgRecallAtK;
        private Double avgPrecisionAtK;
        private Double mrr;
        private Double avgNdcgAtK;
    }
}
