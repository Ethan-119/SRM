package com.srm.modules.rfq.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 供应商综合评分。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierScore {

    private Long supplierId;

    /** 综合评分（0-100） */
    private Integer totalScore;

    /** 价格分 */
    private Double priceScore;

    /** 交期分 */
    private Double deliveryScore;

    /** 质量分 */
    private Double qualityScore;
}
