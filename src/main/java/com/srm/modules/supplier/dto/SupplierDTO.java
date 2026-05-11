package com.srm.modules.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 供应商 DTO — 接收前端请求参数
 */
@Data
public class SupplierDTO {

    /** 供应商编码 */
    @NotBlank(message = "供应商编码不能为空")
    private String supplierCode;

    /** 供应商名称 */
    @NotBlank(message = "供应商名称不能为空")
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

    /** 资质等级: 1-初级 2-中级 3-高级 */
    private Integer qualificationLevel;

    /** 状态: 0-注册 1-待审核 2-已准入 3-合作中 4-冻结 5-黑名单 */
    private Integer status;

    /** 地址 */
    private String address;

    /** 备注 */
    private String remark;
}
