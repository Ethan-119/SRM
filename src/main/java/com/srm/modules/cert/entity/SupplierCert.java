package com.srm.modules.cert.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 供应商资质证书（PG 权威数据源）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_supplier_cert")
public class SupplierCert extends BaseEntity {

    /** 证书编号（唯一） */
    private String certNo;

    /** 供应商 ID */
    private Long supplierId;

    /** 证书类型（如 ISO9001） */
    private String certType;

    /** 签发日期 */
    private LocalDate issueDate;

    /** 过期日期 */
    private LocalDate expireDate;

    /** 状态: 有效 / 过期 */
    private String status;
}
