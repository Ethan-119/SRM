package com.srm.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典项 DTO — 接收前端请求参数
 */
@Data
public class DictItemDTO {

    /** 字典类型 (如: region / category / qualification) */
    @NotBlank(message = "字典类型不能为空")
    private String dictType;

    /** 字典标签 */
    @NotBlank(message = "字典标签不能为空")
    private String label;

    /** 字典值 */
    @NotBlank(message = "字典值不能为空")
    private String value;

    /** 排序 */
    private Integer sort;

    /** 状态: 0-禁用 1-启用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
