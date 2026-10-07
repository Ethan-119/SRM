package com.srm.modules.graph.vo;

import lombok.Data;

import java.util.List;

/**
 * 替代供应商发现结果。
 */
@Data
public class AlternativeSupplierVO {

    /** 替代供应商名称 */
    private String supplierName;

    /** 可替代的物料名称列表 */
    private List<String> materials;

    /** 平均价格差（相对目标供应商，正数表示更贵） */
    private Double priceDiffAvg;

    /** 信用等级 */
    private String creditLevel;
}
