package com.srm.modules.rfq.workflow;

import com.srm.modules.rfq.entity.RfqWorkflow;
import com.srm.modules.rfq.vo.QuoteRecord;
import com.srm.modules.rfq.vo.RiskCheckResult;
import com.srm.modules.rfq.vo.SupplierScore;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次询比价分析执行的上下文。各步骤从 ctx 读输入、把结果写回 ctx。
 */
@Getter
@Setter
public class RfqContext {

    /** 询比价单 */
    private final RfqWorkflow rfq;

    /** 解析后的报价列表（调度器预置） */
    private List<QuoteRecord> quotes;

    /** 各供应商综合评分（评分步骤写入） */
    private List<SupplierScore> scores;

    /** 各供应商风险校验结果（风险步骤写入） */
    private List<RiskCheckResult> risks;

    /** 推荐供应商 ID（推荐步骤写入） */
    private Long recommendSupplierId;

    /** AI 推荐报告（报告步骤写入） */
    private String report;

    /** 步骤错误收集 */
    private final List<String> errors = new ArrayList<>();

    /** 出现失败时的步骤名（最后一个失败步骤） */
    private String errorStep;

    /** 是否已发生不可降级的失败（后续工作步骤跳过，仅记录步骤继续执行） */
    private boolean stopped;

    public RfqContext(RfqWorkflow rfq) {
        this.rfq = rfq;
    }
}
