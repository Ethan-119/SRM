package com.srm.modules.rfq.dto;

import lombok.Data;

/**
 * 采购员确认/驳回请求。
 */
@Data
public class RfqConfirmDTO {

    /** 确认人（采购经理）ID */
    private Long managerId;

    /** true=确认下单，false=驳回 */
    private boolean approved;

    /** 备注/驳回理由 */
    private String comment;
}
