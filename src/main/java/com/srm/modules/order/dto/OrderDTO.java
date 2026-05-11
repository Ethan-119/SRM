package com.srm.modules.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购订单 DTO — 接收前端请求参数
 */
@Data
public class OrderDTO {

    /** 订单编号 */
    @NotBlank(message = "订单编号不能为空")
    private String orderNo;

    /** 供应商ID */
    @NotNull(message = "供应商ID不能为空")
    private Long supplierId;

    /** 物料名称 */
    @NotBlank(message = "物料名称不能为空")
    private String materialName;

    /** 采购数量 */
    @NotNull(message = "采购数量不能为空")
    private Integer quantity;

    /** 单价 */
    @NotNull(message = "单价不能为空")
    private BigDecimal unitPrice;

    /** 总金额 (可为空, 由系统计算) */
    private BigDecimal totalAmount;

    /** 交货日期 */
    private LocalDate deliveryDate;

    /** 状态: 0-待确认 1-生产中 2-已发货 3-已签收 4-已取消 */
    private Integer status;

    /** 备注 */
    private String remark;
}
