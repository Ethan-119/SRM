package com.srm.modules.rfq.enums;

/**
 * 询比价单状态机。
 */
public enum RfqStatus {
    /** 报价中（等待供应商报价） */
    QUOTING,
    /** AI 分析中（报价截止或收齐后自动触发） */
    ANALYZING,
    /** 待采购员确认（AI 报告已生成） */
    PENDING_CONFIRM,
    /** 已确认并生成订单 */
    ORDERED,
    /** 采购员驳回 */
    REJECTED,
    /** 超时流标 */
    EXPIRED
}
