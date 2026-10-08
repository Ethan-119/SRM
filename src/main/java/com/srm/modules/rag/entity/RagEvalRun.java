package com.srm.modules.rag.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RAG 评测批次（一次批量评测的聚合结果）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_rag_eval_run")
public class RagEvalRun extends BaseEntity {

    /** 批次号 */
    private String runNo;

    /** 数据集来源 */
    private String dataset;

    /** 评测 K 值 */
    private Integer topK;

    /** 用例总数 */
    private Integer totalCases;

    private Double avgRecall;
    private Double avgPrecision;
    private Double mrr;
    private Double avgNdcg;
    private Double avgAccuracy;
    private Double avgFaithfulness;

    private Long latencyP50;
    private Long latencyP95;
    private Long latencyP99;
}
