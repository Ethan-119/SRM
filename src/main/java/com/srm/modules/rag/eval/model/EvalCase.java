package com.srm.modules.rag.eval.model;

import lombok.Data;

import java.util.List;

/**
 * RAG 评估用例（对应黄金标准数据集 YAML 中的一条）。
 */
@Data
public class EvalCase {

    /** 用例ID */
    private String id;

    /** 问题 */
    private String question;

    /** 期望命中的文档ID */
    private List<String> expectedDocIds;

    /** 期望答案包含的关键词（用于忠实度/答案质量判断） */
    private List<String> expectedAnswerContains;

    /** 标签（如：价格查询、资质过滤、关联风险） */
    private List<String> tags;

    /** 难度：easy / medium / hard */
    private String difficulty;
}
