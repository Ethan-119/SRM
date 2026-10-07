package com.srm.modules.graph.vo;

import lombok.Data;

import java.util.List;

/**
 * 供应商综合评分结果。
 */
@Data
public class SupplierScoreVO {

    /** 供应商ID */
    private Long supplierId;

    /** 供应商名称 */
    private String supplierName;

    /** 综合得分（0~100） */
    private Double totalScore;

    /** 各维度得分明细 */
    private List<ScoreDimension> dimensions;

    @Data
    public static class ScoreDimension {
        /** 维度名 */
        private String name;

        /** 权重（0~1） */
        private Double weight;

        /** 得分（0~100） */
        private Double score;

        /** 评分依据说明 */
        private String reason;
    }
}
