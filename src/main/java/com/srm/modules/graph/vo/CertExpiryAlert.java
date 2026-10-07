package com.srm.modules.graph.vo;

import lombok.Data;

/**
 * 资质到期提醒。
 */
@Data
public class CertExpiryAlert {

    private Long supplierId;
    private String supplierName;
    private String certType;
    private String certNo;
    private String expireDate;
}
