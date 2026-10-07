package com.srm.modules.rfq.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 分析结果。Map 的 key 使用 String（对应 JSON 对象键）。
 */
@Data
public class AiAnalysisResult {

    /** 推荐供应商 ID */
    private Long recommendSupplierId;

    /** 推荐理由 */
    private String recommendReason;

    /** 各供应商风险警告：key=supplierId 字符串 */
    private Map<String, List<String>> riskWarnings;

    /** 各供应商综合评分：key=supplierId 字符串 */
    private Map<String, Integer> totalScores;
}
