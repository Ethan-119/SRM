package com.srm.modules.graph.vo;

import lombok.Data;

/**
 * 供应链集中度风险（赫芬达尔指数）结果。
 */
@Data
public class ConcentrationRiskVO {

    /** 物料名称 */
    private String materialName;

    /** 供应商数量 */
    private Long supplierCount;

    /** 赫芬达尔指数（0~1） */
    private Double hhi;

    /** 风险等级 */
    private String riskLevel;
}
