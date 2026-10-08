package com.srm.modules.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RAG 评测明细（每条用例的结果）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_rag_eval_result")
public class RagEvalResultEntity extends BaseEntity {

    /** 关联的评测批次 ID */
    private Long runId;

    /** 用例 ID */
    private String caseId;

    /** 问题 */
    private String question;

    /** 检索到的文档 ID（JSON 数组） */
    private String retrievedDocIds;

    private Double recall;
    private Double precision;
    private Double mrr;
    private Double ndcg;
    private Double accuracy;
    private Double faithfulness;

    private Long latencyMs;

    /** 标签（逗号分隔） */
    private String tags;

    private String difficulty;
}
