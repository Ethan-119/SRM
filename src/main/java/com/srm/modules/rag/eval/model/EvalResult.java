package com.srm.modules.rag.eval.model;

import lombok.Data;

import java.util.List;

/**
 * 单条用例的评估结果。
 */
@Data
public class EvalResult {

    /** 用例ID */
    private String caseId;

    /** 问题 */
    private String question;

    /** 实际检索到的文档ID（按排序） */
    private List<String> retrievedDocIds;

    /** Recall@K */
    private Double recallAtK;

    /** Precision@K */
    private Double precisionAtK;

    /** 倒数排名（1 / 首个命中位置），MRR 取其均值 */
    private Double reciprocalRank;

    /** NDCG@K */
    private Double ndcgAtK;

    /** 答案准确率（0~1，命中 expectedAnswerContains 关键词比例；未启用时为 null） */
    private Double accuracy;

    /** 检索延迟（毫秒） */
    private Long latencyMs;

    /** 忠实度（0~1，LLM Judge 产出，未启用时为 null） */
    private Double faithfulness;

    /** 标签 */
    private List<String> tags;

    /** 难度 */
    private String difficulty;
}
