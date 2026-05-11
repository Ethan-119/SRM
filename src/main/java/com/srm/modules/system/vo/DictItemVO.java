package com.srm.modules.system.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典项 VO — 返回给前端的展示数据
 */
@Data
public class DictItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 字典类型 */
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
