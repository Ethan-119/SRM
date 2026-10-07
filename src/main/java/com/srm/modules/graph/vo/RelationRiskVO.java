package com.srm.modules.graph.vo;

import lombok.Data;

import java.util.List;

/**
 * 关联风险穿透结果。
 */
@Data
public class RelationRiskVO {

    /** 关联供应商名称 */
    private String relatedName;

    /** 关联深度（跳数） */
    private Long depth;

    /** 关系链（依次的关系类型） */
    private List<String> relationChain;
}
