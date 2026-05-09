package com.srm.modules.supplier.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 供应商
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_supplier")
public class Supplier extends BaseEntity {

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

    /** 资质等级: 1-初级 2-中级 3-高级 */
    private Integer qualificationLevel;

    /** 状态: 0-注册 1-待审核 2-已准入 3-合作中 4-冻结 5-黑名单 */
    private Integer status;

    /** 地址 */
    private String address;

    /** 备注 */
    private String remark;
}
