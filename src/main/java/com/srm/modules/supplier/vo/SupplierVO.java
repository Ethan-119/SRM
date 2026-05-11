package com.srm.modules.supplier.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 供应商 VO — 返回给前端的展示数据 (隐藏 isDeleted 等内部字段)
 */
@Data
public class SupplierVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 供应商编码 */
    private String supplierCode;

    /** 供应商名称 */
    private String supplierName;

    /** 联系人 */
    private String contactPerson;

    /** 联系电话 */
    private String contactPhone;

    /** 邮箱 */
    private String email;

    /** 所属地区 */
    private String region;

    /** 主营品类 */
    private String mainCategory;

    /** 资质等级 */
    private Integer qualificationLevel;

    /** 状态 */
    private Integer status;

    /** 地址 */
    private String address;

    /** 备注 */
    private String remark;
}
