package com.srm.modules.rfq.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 询比价工作流实例。供应商列表、报价、AI 分析结果均以 JSON 字符串存储。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_rfq_workflow")
public class RfqWorkflow extends BaseEntity {

    /** 询比价单号 */
    private String rfqNo;

    /** 物料名称 */
    private String materialName;

    /** 采购数量 */
    private BigDecimal quantity;

    /** 参与供应商 ID 列表（JSON 数组） */
    private String supplierIds;

    /** 报价结果（JSON 数组） */
    private String quotes;

    /** AI 分析结果（JSON 对象） */
    private String aiAnalysis;

    /** 状态：QUOTING/ANALYZING/PENDING_CONFIRM/ORDERED/REJECTED/EXPIRED */
    private String status;

    /** 报价截止时间 */
    private LocalDateTime quoteDeadline;

    /** 确认人 */
    private Long confirmBy;

    /** 确认时间 */
    private LocalDateTime confirmTime;

    /** 生成的订单 ID */
    private Long orderId;
}
