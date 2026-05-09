package com.srm.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.srm.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典项
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srm_dict_item")
public class DictItem extends BaseEntity {

    /** 字典类型 (如: region / category / qualification) */
    private String dictType;

    /** 字典标签 */
    private String label;

    /** 字典值 */
    private String value;

    /** 排序 */
    private Integer sort;

    /** 状态: 0-禁用 1-启用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
