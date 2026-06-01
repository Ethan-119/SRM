package com.srm.modules.supplier.dto;

import lombok.Data;

@Data
public class SupplierPageDTO {

    /** 页码，默认1 */
    private Long pageNum = 1L;

    /** 每页条数，默认10 */
    private Long pageSize = 10L;

    /** 供应商名称模糊匹配 */
    private String supplierName;

    /** 状态精确匹配 */
    private Integer status;
}
