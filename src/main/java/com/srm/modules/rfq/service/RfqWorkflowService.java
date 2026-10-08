package com.srm.modules.rfq.service;

import cn.hutool.json.JSONUtil;
import com.srm.common.exception.BusinessException;
import com.srm.modules.notification.service.NotificationService;
import com.srm.modules.order.entity.Order;
import com.srm.modules.order.service.OrderService;
import com.srm.modules.rfq.entity.RfqWorkflow;
import com.srm.modules.rfq.enums.RfqStatus;
import com.srm.modules.rfq.mapper.RfqWorkflowMapper;
import com.srm.modules.rfq.vo.AiAnalysisResult;
import com.srm.modules.rfq.vo.QuoteRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 智能询比价工作流（交互入口）。
 *
 * <p>流程：创建询比价单 → 供应商报价（限时）→ 自动进入分析 → 采购员确认 → 生成订单。
 * 其中的「AI 评分/风险/推荐/报告」已拆分为步骤链，由 {@code RfqWorkflowScheduler}
 * 驱动 {@code RfqStepChain} 异步执行，本类只保留交互入口与状态迁移。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RfqWorkflowService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    /** 报价锁：按「询价单 + 供应商」加锁（供应商 = 用户） */
    private static final String QUOTE_LOCK_PREFIX = "srm:rfq:lock:quote:";
    /** 确认锁：按「询价单 + 采购员」加锁（采购员 = 用户） */
    private static final String CONFIRM_LOCK_PREFIX = "srm:rfq:lock:confirm:";
    private static final long LOCK_TTL_SECONDS = 30;

    private final RfqWorkflowMapper rfqMapper;
    private final OrderService orderService;
    private final NotificationService notificationService;
    private final RfqLockService lockService;

    // ==================== 创建询比价单 ====================

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

    // ==================== 供应商报价 ====================

    @Transactional
    public void submitQuote(Long rfqId, Long supplierId, BigDecimal price,
                            BigDecimal taxRate, int deliveryDays) {
        String lockKey = QUOTE_LOCK_PREFIX + rfqId + ":" + supplierId;
        if (!lockService.tryLock(lockKey, LOCK_TTL_SECONDS)) {
            throw new BusinessException("该供应商报价正在处理中，请勿重复提交");
        }
        try {
            doSubmitQuote(rfqId, supplierId, price, taxRate, deliveryDays);
        } finally {
            lockService.unlock(lockKey);
        }
    }

    private void doSubmitQuote(Long rfqId, Long supplierId, BigDecimal price,
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

    // ==================== 触发分析（截止 / 手动） ====================

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

    // ==================== 采购员确认 / 驳回 ====================

    @Transactional
    public Long confirmRfq(Long rfqId, Long managerId, boolean approved, String comment) {
        String lockKey = CONFIRM_LOCK_PREFIX + rfqId + ":" + managerId;
        if (!lockService.tryLock(lockKey, LOCK_TTL_SECONDS)) {
            throw new BusinessException("确认操作正在处理中，请稍后再试");
        }
        try {
            return doConfirm(rfqId, managerId, approved, comment);
        } finally {
            lockService.unlock(lockKey);
        }
    }

    private Long doConfirm(Long rfqId, Long managerId, boolean approved, String comment) {
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
}
