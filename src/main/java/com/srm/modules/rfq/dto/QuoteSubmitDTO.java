package com.srm.modules.rfq.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 供应商报价请求。
 */
@Data
public class QuoteSubmitDTO {

    @NotNull(message = "询比价单ID不能为空")
    private Long rfqId;

    @NotNull(message = "供应商ID不能为空")
    private Long supplierId;

    @NotNull(message = "报价不能为空")
    private BigDecimal price;

    /** 税率 */
    private BigDecimal taxRate;

    @NotNull(message = "交期不能为空")
    private Integer deliveryDays;
}
