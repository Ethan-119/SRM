package com.srm.modules.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购订单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_purchase_order")
public class Order extends BaseEntity {

    /** 订单编号 */
    private String orderNo;

    /** 供应商ID */
    private Long supplierId;

    /** 物料名称 */
    private String materialName;

    /** 采购数量 */
    private Integer quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 总金额 */
    private BigDecimal totalAmount;

    /** 交货日期 */
    private LocalDate deliveryDate;

    /** 状态: 0-待确认 1-生产中 2-已发货 3-已签收 4-已取消 */
    private Integer status;

    /** 签收时间 */
    private LocalDateTime receivedTime;

    /** 备注 */
    private String remark;
}
