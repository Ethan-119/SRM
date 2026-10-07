package com.srm.modules.rfq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建询比价单请求。
 */
@Data
public class RfqCreateRequest {

    @NotBlank(message = "物料名称不能为空")
    private String materialName;

    @NotNull(message = "采购数量不能为空")
    private BigDecimal quantity;

    @NotEmpty(message = "参与报价的供应商不能为空")
    private List<Long> supplierIds;

    /** 报价时限（小时），默认 24 */
    private Integer quoteHours;
}
