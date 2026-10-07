package com.srm.modules.rfq.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 供应商风险校验结果。
 */
@Data
public class RiskCheckResult {

    private Long supplierId;

    /** LOW / HIGH */
    private String riskLevel;

    /** 风险警告列表 */
    private List<String> warnings = new ArrayList<>();
}
