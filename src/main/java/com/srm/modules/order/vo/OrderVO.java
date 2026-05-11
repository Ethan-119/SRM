package com.srm.modules.order.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购订单 VO — 返回给前端的展示数据
 */
@Data
public class OrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

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
