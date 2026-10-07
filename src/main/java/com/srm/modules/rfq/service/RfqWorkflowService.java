package com.srm.modules.rfq.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.srm.common.exception.BusinessException;
import com.srm.modules.graph.repository.Neo4jSupplierRepository;
import com.srm.modules.notification.service.NotificationService;
import com.srm.modules.order.entity.Order;
import com.srm.modules.order.service.OrderService;
import com.srm.modules.rfq.entity.RfqWorkflow;
import com.srm.modules.rfq.enums.RfqStatus;
import com.srm.modules.rfq.mapper.RfqWorkflowMapper;
import com.srm.modules.rfq.vo.AiAnalysisResult;
import com.srm.modules.rfq.vo.QuoteRecord;
import com.srm.modules.rfq.vo.RiskCheckResult;
import com.srm.modules.rfq.vo.SupplierScore;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 智能询比价全自动闭环工作流。
 *
 * 流程：创建询比价单 → 供应商报价（限时）→ 全自动 AI 评分 + 图谱风险校验
 * → 生成推荐报告 → 采购员一键确认 → 自动生成采购订单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RfqWorkflowService {

    private final RfqWorkflowMapper rfqMapper;
    private final SupplierScoringService scoringService;
    private final Neo4jSupplierRepository neo4jRepo;
    private final OrderService orderService;
    private final NotificationService notificationService;
    private final SupplierService supplierService;
    private final ChatClient chatClient;

    /** 自注入代理，保证 @Async / @Transactional 生效。 */
    @Lazy
    @Autowired
    private RfqWorkflowService self;

    /** 集中度风险阈值：供应商集团占该物料采购额超过 25% 判为高风险 */
    private static final double CONCENTRATION_THRESHOLD = 0.25;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    // ==================== Step 1: 创建询比价单 ====================

    @Transactional
    public RfqWorkflow createRfq(String materialName, BigDecimal quantity,
                                 List<Long> supplierIds, int quoteHours) {
        String rfqNo = "RFQ-" + LocalDate.now().format(DATE_FMT)
                + "-" + (System.currentTimeMillis() % 100000);
        RfqWorkflow rfq = new RfqWorkflow();
        rfq.setRfqNo(rfqNo);
        rfq.setMaterialName(materialName);
        rfq.setQuantity(quantity);
        rfq.setSupplierIds(JSONUtil.toJsonStr(supplierIds));
        rfq.setQuotes(JSONUtil.toJsonStr(new ArrayList<QuoteRecord>()));
        rfq.setStatus(RfqStatus.QUOTING.name());
        rfq.setQuoteDeadline(LocalDateTime.now().plusHours(quoteHours));
        rfqMapper.insert(rfq);

        supplierIds.forEach(id -> notificationService.notifySupplier(id,
                "您收到询比价邀请 " + rfqNo + "（物料：" + materialName + "），请在截止时间前报价。"));
        log.info("询比价单创建: {}, 等待 {} 家供应商报价", rfqNo, supplierIds.size());
        return rfq;
    }

    // ==================== Step 2: 供应商报价 ====================

    @Transactional
    public void submitQuote(Long rfqId, Long supplierId, BigDecimal price,
                            BigDecimal taxRate, int deliveryDays) {
        RfqWorkflow rfq = rfqMapper.selectById(rfqId);
        if (rfq == null) {
            throw new BusinessException("询比价单不存在");
        }
        if (!RfqStatus.QUOTING.name().equals(rfq.getStatus())) {
            throw new BusinessException("当前状态不允许报价: " + rfq.getStatus());
        }
        if (LocalDateTime.now().isAfter(rfq.getQuoteDeadline())) {
            throw new BusinessException("已超过报价截止时间");
        }

        List<Long> expected = parseSupplierIds(rfq.getSupplierIds());
        if (!expected.contains(supplierId)) {
            throw new BusinessException("该供应商不在本次询比价范围内");
        }

        List<QuoteRecord> quotes = parseQuotes(rfq.getQuotes());
        quotes.removeIf(q -> q.getSupplierId().equals(supplierId)); // 重复报价覆盖
        QuoteRecord quote = new QuoteRecord();
        quote.setSupplierId(supplierId);
        quote.setPrice(price);
        quote.setTaxRate(taxRate);
        quote.setDeliveryDays(deliveryDays);
        quote.setQuoteTime(LocalDateTime.now());
        quotes.add(quote);
        rfq.setQuotes(JSONUtil.toJsonStr(quotes));
        rfqMapper.updateById(rfq);

        log.info("供应商 {} 报价 ¥{}，询比价单 {}", supplierId, price, rfq.getRfqNo());

        // 全部报价齐 → 置为待分析，由调度线程统一驱动 AI 分析
        Set<Long> quoted = quotes.stream().map(QuoteRecord::getSupplierId).collect(Collectors.toSet());
        if (quoted.containsAll(expected)) {
            rfq.setStatus(RfqStatus.ANALYZING.name());
            rfqMapper.updateById(rfq);
            log.info("所有供应商已报价，询比价单 {} 进入分析队列", rfq.getRfqNo());
        }
    }

    // ==================== Step 3: 触发分析（截止 / 手动） ====================

    @Transactional
    public void triggerAnalysis(Long rfqId) {
        RfqWorkflow rfq = rfqMapper.selectById(rfqId);
        if (rfq == null) {
            throw new BusinessException("询比价单不存在");
        }
        if (!RfqStatus.QUOTING.name().equals(rfq.getStatus())) {
            return;
        }
        rfq.setStatus(RfqStatus.ANALYZING.name());
        rfqMapper.updateById(rfq);
        log.info("询比价单 {} 触发 AI 分析", rfq.getRfqNo());
    }

    /** 调度线程：扫描「分析中」的询比价单，异步执行 AI 分析。 */
    @Scheduled(fixedDelay = 30000)
    public void runPendingAnalysis() {
        List<RfqWorkflow> pendings = rfqMapper.selectList(
                new LambdaQueryWrapper<RfqWorkflow>()
                        .eq(RfqWorkflow::getStatus, RfqStatus.ANALYZING.name()));
        for (RfqWorkflow rfq : pendings) {
            self.triggerAsyncAnalysis(rfq.getId());
        }
    }

    /** 调度线程：报价截止的询比价单自动进入分析。 */
    @Scheduled(fixedDelay = 30000)
    public void checkDeadlineAndTrigger() {
        List<RfqWorkflow> expired = rfqMapper.selectList(
                new LambdaQueryWrapper<RfqWorkflow>()
                        .eq(RfqWorkflow::getStatus, RfqStatus.QUOTING.name())
                        .le(RfqWorkflow::getQuoteDeadline, LocalDateTime.now()));
        for (RfqWorkflow rfq : expired) {
            rfq.setStatus(RfqStatus.ANALYZING.name());
            rfqMapper.updateById(rfq);
            log.info("询比价单 {} 报价截止，进入分析", rfq.getRfqNo());
        }
    }

    @Async
    public void triggerAsyncAnalysis(Long rfqId) {
        self.executeAiAnalysis(rfqId);
    }

    // ==================== Step 4: 全自动 AI 分析 ====================

    @Transactional
    public void executeAiAnalysis(Long rfqId) {
        RfqWorkflow rfq = rfqMapper.selectById(rfqId);
        if (rfq == null || !RfqStatus.ANALYZING.name().equals(rfq.getStatus())) {
            return;
        }

        List<QuoteRecord> quotes = parseQuotes(rfq.getQuotes());
        if (quotes.isEmpty()) {
            rfq.setStatus(RfqStatus.EXPIRED.name());
            rfqMapper.updateById(rfq);
            log.info("询比价单 {} 无有效报价，已流标", rfq.getRfqNo());
            return;
        }

        try {
            // 4.1 综合评分
            BigDecimal minPrice = quotes.stream().map(QuoteRecord::getPrice)
                    .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            int minDelivery = quotes.stream().mapToInt(QuoteRecord::getDeliveryDays).min().orElse(1);
            List<SupplierScore> scores = quotes.stream()
                    .map(q -> scoringService.calculate(q.getSupplierId(), q.getPrice(),
                            q.getDeliveryDays(), minPrice, minDelivery))
                    .toList();

            // 4.2 关联风险 + 集中度风险
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

            // 4.3 推荐供应商（过滤高风险后取最高分）
            Long recommendSupplierId = determineRecommendation(scores, risks);

            // 4.4 AI 生成推荐理由
            String recommendReason = generateAiReport(rfq, quotes, scores, risks, recommendSupplierId);

            // 4.5 保存分析结果
            AiAnalysisResult analysis = new AiAnalysisResult();
            analysis.setRecommendSupplierId(recommendSupplierId);
            analysis.setRecommendReason(recommendReason);
            analysis.setRiskWarnings(risks.stream().filter(r -> !r.getWarnings().isEmpty())
                    .collect(Collectors.toMap(r -> String.valueOf(r.getSupplierId()), RiskCheckResult::getWarnings)));
            analysis.setTotalScores(scores.stream()
                    .collect(Collectors.toMap(s -> String.valueOf(s.getSupplierId()), SupplierScore::getTotalScore)));
            rfq.setAiAnalysis(JSONUtil.toJsonStr(analysis));
            rfq.setStatus(RfqStatus.PENDING_CONFIRM.name());
            rfqMapper.updateById(rfq);

            notificationService.notifyManager("询比价单 " + rfq.getRfqNo()
                    + " 分析完成，推荐供应商 " + supplierName(recommendSupplierId) + "，请确认。");
            log.info("询比价单 {} 分析完成，推荐供应商 {}", rfq.getRfqNo(), recommendSupplierId);
        } catch (Exception e) {
            log.error("AI 分析失败, rfqId={}", rfqId, e);
            rfq.setStatus(RfqStatus.PENDING_CONFIRM.name());
            rfqMapper.updateById(rfq);
        }
    }

    // ==================== Step 5: 一键确认 ====================

    @Transactional
    public Long confirmRfq(Long rfqId, Long managerId, boolean approved, String comment) {
        RfqWorkflow rfq = rfqMapper.selectById(rfqId);
        if (rfq == null) {
            throw new BusinessException("询比价单不存在");
        }
        if (!RfqStatus.PENDING_CONFIRM.name().equals(rfq.getStatus())) {
            throw new BusinessException("当前状态不允许确认: " + rfq.getStatus());
        }

        if (!approved) {
            rfq.setStatus(RfqStatus.REJECTED.name());
            rfq.setConfirmBy(managerId);
            rfq.setConfirmTime(LocalDateTime.now());
            rfqMapper.updateById(rfq);
            log.info("询比价单 {} 被驳回: {}", rfq.getRfqNo(), comment);
            return null;
        }

        AiAnalysisResult analysis = JSONUtil.toBean(rfq.getAiAnalysis(), AiAnalysisResult.class);
        Long supplierId = analysis.getRecommendSupplierId();
        if (supplierId == null) {
            throw new BusinessException("无推荐供应商，无法自动下单，请人工处理");
        }

        Long orderId = createOrder(rfq, supplierId, managerId);

        rfq.setStatus(RfqStatus.ORDERED.name());
        rfq.setConfirmBy(managerId);
        rfq.setConfirmTime(LocalDateTime.now());
        rfq.setOrderId(orderId);
        rfqMapper.updateById(rfq);

        notificationService.notifySupplier(supplierId,
                "您的报价已被采纳，已生成采购订单 " + rfq.getRfqNo());
        log.info("询比价单 {} 已确认，生成订单 {}", rfq.getRfqNo(), orderId);
        return orderId;
    }

    // ==================== 查询 ====================

    public RfqWorkflow getById(Long id) {
        return rfqMapper.selectById(id);
    }

    public List<RfqWorkflow> list() {
        return rfqMapper.selectList(null);
    }

    // ==================== 私有方法 ====================

    private Long createOrder(RfqWorkflow rfq, Long supplierId, Long managerId) {
        QuoteRecord quote = parseQuotes(rfq.getQuotes()).stream()
                .filter(q -> q.getSupplierId().equals(supplierId)).findFirst()
                .orElseThrow(() -> new BusinessException("推荐供应商报价缺失"));

        Order order = new Order();
        order.setOrderNo("PO-" + LocalDate.now().format(DATE_FMT) + "-" + (System.currentTimeMillis() % 100000));
        order.setSupplierId(supplierId);
        order.setMaterialName(rfq.getMaterialName());
        int qty = rfq.getQuantity() == null ? 1 : rfq.getQuantity().intValue();
        order.setQuantity(qty);
        order.setUnitPrice(quote.getPrice());
        order.setTotalAmount(quote.getPrice().multiply(BigDecimal.valueOf(qty)));
        order.setDeliveryDate(LocalDate.now().plusDays(quote.getDeliveryDays() == null ? 0 : quote.getDeliveryDays()));
        order.setStatus(0); // 待确认
        order.setRemark("由询比价单 " + rfq.getRfqNo() + " 自动生成");
        order.setCreateBy(managerId);
        orderService.save(order);
        return order.getId();
    }

    private Long determineRecommendation(List<SupplierScore> scores, List<RiskCheckResult> risks) {
        Set<Long> highRisk = risks.stream()
                .filter(r -> "HIGH".equals(r.getRiskLevel()))
                .map(RiskCheckResult::getSupplierId)
                .collect(Collectors.toSet());
        return scores.stream()
                .filter(s -> !highRisk.contains(s.getSupplierId()))
                .max(Comparator.comparing(SupplierScore::getTotalScore))
                .map(SupplierScore::getSupplierId)
                .orElse(null);
    }

    private String generateAiReport(RfqWorkflow rfq, List<QuoteRecord> quotes,
                                    List<SupplierScore> scores, List<RiskCheckResult> risks,
                                    Long recommendSupplierId) {
        Map<Long, Supplier> supplierMap = supplierService.list().stream()
                .collect(Collectors.toMap(Supplier::getId, s -> s, (a, b) -> a));

        StringBuilder prompt = new StringBuilder();
        prompt.append("作为采购决策助手，请基于以下数据生成推荐报告（100字以内）：\n");
        prompt.append("物料: ").append(rfq.getMaterialName())
                .append("，数量: ").append(rfq.getQuantity()).append("\n");
        for (QuoteRecord q : quotes) {
            SupplierScore score = scores.stream().filter(s -> s.getSupplierId().equals(q.getSupplierId()))
                    .findFirst().orElse(null);
            RiskCheckResult risk = risks.stream().filter(r -> r.getSupplierId().equals(q.getSupplierId()))
                    .findFirst().orElse(null);
            Supplier sp = supplierMap.get(q.getSupplierId());
            String name = sp == null ? "供应商#" + q.getSupplierId() : sp.getSupplierName();
            prompt.append("- ").append(name)
                    .append(": 单价¥").append(q.getPrice()).append("，交期").append(q.getDeliveryDays())
                    .append("天，评分").append(score == null ? 0 : score.getTotalScore())
                    .append("，风险:").append(risk == null || risk.getWarnings().isEmpty() ? "无" : String.join("；", risk.getWarnings()))
                    .append("\n");
        }
        prompt.append("推荐供应商: ").append(supplierName(recommendSupplierId))
                .append("。请说明推荐理由（含价格/交期/风险权衡）。");

        try {
            return chatClient.prompt().user(prompt.toString()).call().content();
        } catch (Exception e) {
            log.warn("AI 报告生成失败，使用默认推荐理由", e);
            return "综合评分最高，风险可控";
        }
    }

    private List<Long> parseSupplierIds(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return JSONUtil.toList(json, Long.class);
    }

    private List<QuoteRecord> parseQuotes(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return JSONUtil.toList(json, QuoteRecord.class);
    }

    private String supplierName(Long supplierId) {
        if (supplierId == null) {
            return "未知";
        }
        Supplier s = supplierService.getById(supplierId);
        return s == null ? "供应商#" + supplierId : s.getSupplierName();
    }
}
