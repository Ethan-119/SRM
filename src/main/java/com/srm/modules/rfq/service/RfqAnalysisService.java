package com.srm.modules.rfq.service;

import com.srm.modules.graph.repository.Neo4jSupplierRepository;
import com.srm.modules.rfq.entity.RfqWorkflow;
import com.srm.modules.rfq.vo.AiAnalysisResult;
import com.srm.modules.rfq.vo.QuoteRecord;
import com.srm.modules.rfq.vo.RiskCheckResult;
import com.srm.modules.rfq.vo.SupplierScore;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 询比价共享分析服务（唯一实现）。
 *
 * <p>评分 / 风险 / 推荐 / 报告 集中在此，Agent 工具与（若未来需要）工作流都调它，
 * 避免评分与图谱工具两套逻辑并存。两套评分分工：</p>
 * <ul>
 *   <li>评分 = 本次报价的「当场考试分」{@link SupplierScoringService}；</li>
 *   <li>风险 = 图谱「履历 / 关联风险」{@link Neo4jSupplierRepository}。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RfqAnalysisService {

    private static final double CONCENTRATION_THRESHOLD = 0.25;

    private final SupplierScoringService scoringService;
    private final Neo4jSupplierRepository neo4jRepo;
    private final SupplierService supplierService;
    private final ChatClient chatClient;

    /** 分析：评分 → 风险 → 推荐 → 报告，返回可直接落库的分析结果。 */
    public AiAnalysisResult analyze(RfqWorkflow rfq, List<QuoteRecord> quotes) {
        // 1. 综合评分（本次报价归一化）
        BigDecimal minPrice = quotes.stream().map(QuoteRecord::getPrice)
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        int minDelivery = quotes.stream()
                .mapToInt(q -> q.getDeliveryDays() == null ? 1 : q.getDeliveryDays())
                .min().orElse(1);
        List<SupplierScore> scores = quotes.stream()
                .map(q -> scoringService.calculate(q.getSupplierId(), q.getPrice(),
                        q.getDeliveryDays() == null ? 1 : q.getDeliveryDays(), minPrice, minDelivery))
                .toList();

        // 2. 风险校验（图谱关联风险 + 集中度风险）
        List<RiskCheckResult> risks = quotes.stream().map(q -> {
            List<String> warnings = new ArrayList<>();
            List<String> related = neo4jRepo.getRelatedRisks(q.getSupplierId());
            if (!related.isEmpty()) {
                warnings.add("关联风险: 与 " + String.join("、", related) + " 存在关联");
            }
            Double concentration = neo4jRepo.getConcentrationRisk(q.getSupplierId(), rfq.getMaterialName());
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

        // 3. 推荐供应商（过滤高风险，取综合评分最高）
        Set<Long> highRisk = risks.stream()
                .filter(r -> "HIGH".equals(r.getRiskLevel()))
                .map(RiskCheckResult::getSupplierId)
                .collect(Collectors.toSet());
        Long recommend = scores.stream()
                .filter(s -> !highRisk.contains(s.getSupplierId()))
                .max(Comparator.comparing(SupplierScore::getTotalScore))
                .map(SupplierScore::getSupplierId)
                .orElse(null);

        // 4. 报告（LLM 生成，失败降级为默认理由）
        String report = generateReport(rfq, quotes, scores, risks, recommend);

        AiAnalysisResult analysis = new AiAnalysisResult();
        analysis.setRecommendSupplierId(recommend);
        analysis.setRecommendReason(report);
        analysis.setRiskWarnings(risks.stream().filter(r -> !r.getWarnings().isEmpty())
                .collect(Collectors.toMap(r -> String.valueOf(r.getSupplierId()), RiskCheckResult::getWarnings)));
        analysis.setTotalScores(scores.stream()
                .collect(Collectors.toMap(s -> String.valueOf(s.getSupplierId()), SupplierScore::getTotalScore)));
        return analysis;
    }

    private String generateReport(RfqWorkflow rfq, List<QuoteRecord> quotes,
                                  List<SupplierScore> scores, List<RiskCheckResult> risks, Long recommend) {
        try {
            Map<Long, Supplier> supplierMap = supplierService.list().stream()
                    .collect(Collectors.toMap(Supplier::getId, s -> s, (a, b) -> a));

            StringBuilder prompt = new StringBuilder();
            prompt.append("作为采购决策助手，请基于以下数据生成推荐报告（100字以内）：\n");
            prompt.append("物料: ").append(rfq.getMaterialName())
                    .append("，数量: ").append(rfq.getQuantity()).append("\n");
            for (QuoteRecord q : quotes) {
                SupplierScore score = scores.stream()
                        .filter(s -> s.getSupplierId().equals(q.getSupplierId())).findFirst().orElse(null);
                RiskCheckResult risk = risks.stream()
                        .filter(r -> r.getSupplierId().equals(q.getSupplierId())).findFirst().orElse(null);
                Supplier sp = supplierMap.get(q.getSupplierId());
                String name = sp == null ? "供应商#" + q.getSupplierId() : sp.getSupplierName();
                prompt.append("- ").append(name)
                        .append(": 单价¥").append(q.getPrice()).append("，交期").append(q.getDeliveryDays())
                        .append("天，评分").append(score == null ? 0 : score.getTotalScore())
                        .append("，风险:").append(risk == null || risk.getWarnings().isEmpty()
                                ? "无" : String.join("；", risk.getWarnings()))
                        .append("\n");
            }
            prompt.append("推荐供应商: ").append(supplierName(recommend))
                    .append("。请说明推荐理由（含价格/交期/风险权衡）。");

            return chatClient.prompt().user(prompt.toString()).call().content();
        } catch (Exception e) {
            log.warn("AI 报告生成失败，使用默认推荐理由 rfqId={}", rfq.getId(), e);
            return "综合评分最高，风险可控";
        }
    }

    private String supplierName(Long supplierId) {
        if (supplierId == null) {
            return "未知";
        }
        Supplier s = supplierService.getById(supplierId);
        return s == null ? "供应商#" + supplierId : s.getSupplierName();
    }
}
