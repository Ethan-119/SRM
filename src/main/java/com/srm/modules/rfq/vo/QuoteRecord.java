package com.srm.modules.rfq.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单条供应商报价记录。
 */
@Data
public class QuoteRecord {

    /** 供应商 ID */
    private Long supplierId;

    /** 报价单价 */
    private BigDecimal price;

    /** 税率 */
    private BigDecimal taxRate;

    /** 交期（天） */
    private Integer deliveryDays;

    /** 报价时间 */
    private LocalDateTime quoteTime;
}
