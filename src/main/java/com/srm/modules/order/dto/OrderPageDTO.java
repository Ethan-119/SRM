package com.srm.modules.order.dto;

import lombok.Data;

@Data
public class OrderPageDTO {

    /** 页码，默认1 */
    private Long pageNum = 1L;

    /** 每页条数，默认10 */
    private Long pageSize = 10L;

    /** 订单编号模糊匹配 */
    private String orderNo;

    /** 状态精确匹配 */
    private Integer status;
}
