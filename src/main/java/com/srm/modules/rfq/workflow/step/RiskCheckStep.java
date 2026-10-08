package com.srm.modules.rfq.workflow.step;

import com.srm.modules.graph.repository.Neo4jSupplierRepository;
import com.srm.modules.rfq.vo.QuoteRecord;
import com.srm.modules.rfq.vo.RiskCheckResult;
import com.srm.modules.rfq.workflow.RfqContext;
import com.srm.modules.rfq.workflow.RfqStep;
import com.srm.modules.rfq.workflow.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 步骤2：风险校验（失败中断）。
 * 基于 Neo4j 图谱做关联风险穿透 + 集中度风险检查。
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class RiskCheckStep implements RfqStep {

    private static final double CONCENTRATION_THRESHOLD = 0.25;

    private final Neo4jSupplierRepository neo4jRepo;

    @Override
    public String name() {
        return "风险校验";
    }

    @Override
    public StepResult execute(RfqContext ctx) {
        List<QuoteRecord> quotes = ctx.getQuotes();
        List<RiskCheckResult> risks = quotes.stream().map(q -> {
            List<String> warnings = new ArrayList<>();
            List<String> related = neo4jRepo.getRelatedRisks(q.getSupplierId());
            if (!related.isEmpty()) {
                warnings.add("关联风险: 与 " + String.join("、", related) + " 存在关联");
            }
            Double concentration = neo4jRepo.getConcentrationRisk(q.getSupplierId(), ctx.getRfq().getMaterialName());
            if (concentration != null && concentration > CONCENTRATION_THRESHOLD) {
                warnings.add("集中度风险: 该供应商集团占该物料采购额 "
                        + String.format("%.0f%%", concentration * 100));
            }
            RiskCheckResult r = new RiskCheckResult();
            r.setSupplierId(q.getSupplierId());
            r.setRiskLevel(warnings.isEmpty() ? "LOW" : "HIGH");
            r.setWarnings(warnings);
            return r;
        }).toList();
        ctx.setRisks(risks);
        return StepResult.success("风险校验完成");
    }
}
