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
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 询比价工作流（交互入口）。
 *
 * <p>定位：RFQ 作为 Agent 的「深度分析」工具，由 Chat 统一入口驱动，同步完成
 * 「创建单 + 模拟收报价 + 分析 + 留痕」，不再依赖定时轮询与异步状态机。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RfqWorkflowService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    /** 确认锁：按「询价单 + 采购员」加锁（采购员 = 用户） */
    private static final String CONFIRM_LOCK_PREFIX = "srm:rfq:lock:confirm:";
    private static final long LOCK_TTL_SECONDS = 30;

    private final RfqWorkflowMapper rfqMapper;
    private final RfqAnalysisService analysisService;
    private final OrderService orderService;
    private final SupplierService supplierService;
    private final NotificationService notificationService;
    private final RfqLockService lockService;

    // ==================== 创建 + 分析（同步） ====================

    /** 创建询比价单 + 模拟报价 + 同步分析 + 留痕。 */
    @Transactional
    public RfqWorkflow createAndAnalyze(String materialName, BigDecimal quantity, List<Long> supplierIds) {
        List<Long> suppliers = (supplierIds == null || supplierIds.isEmpty())
                ? defaultSuppliers() : supplierIds;
        if (suppliers.isEmpty()) {
            throw new BusinessException("没有可参与询比价的供应商");
        }

        String rfqNo = "RFQ-" + LocalDate.now().format(DATE_FMT)
                + "-" + (System.currentTimeMillis() % 100000);
        RfqWorkflow rfq = new RfqWorkflow();
        rfq.setRfqNo(rfqNo);
        rfq.setMaterialName(materialName);
        rfq.setQuantity(quantity);
        rfq.setSupplierIds(JSONUtil.toJsonStr(suppliers));

        List<QuoteRecord> quotes = simulateQuotes(suppliers, materialName);
        rfq.setQuotes(JSONUtil.toJsonStr(quotes));

        AiAnalysisResult analysis = analysisService.analyze(rfq, quotes);
        rfq.setAiAnalysis(JSONUtil.toJsonStr(analysis));
        rfq.setStatus(RfqStatus.PENDING_CONFIRM.name());
        rfqMapper.insert(rfq);

        notificationService.notifyManager("询比价单 " + rfqNo + " 分析完成，推荐供应商 "
                + supplierName(analysis.getRecommendSupplierId()) + "，请确认。");
        log.info("询比价单 {} 创建并分析完成，推荐供应商 {}", rfqNo, analysis.getRecommendSupplierId());
        return rfq;
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

    /** 默认参与方：状态为「合作中」(3) 的供应商。 */
    private List<Long> defaultSuppliers() {
        return supplierService.list().stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 3)
                .map(Supplier::getId)
                .toList();
    }

    /** 模拟报价：以历史成交均价为基准，按供应商 ID 做确定性浮动，保证可复现。 */
    private List<QuoteRecord> simulateQuotes(List<Long> supplierIds, String materialName) {
        BigDecimal base = historicalAvgPrice(materialName);
        if (base == null || base.signum() <= 0) {
            base = BigDecimal.valueOf(100);
        }
        List<QuoteRecord> quotes = new ArrayList<>();
        for (Long id : supplierIds) {
            QuoteRecord q = new QuoteRecord();
            q.setSupplierId(id);
            double factor = 0.88 + (Math.abs(id % 7)) * 0.04; // 0.88 ~ 1.12
            q.setPrice(base.multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP));
            q.setTaxRate(new BigDecimal("13"));
            q.setDeliveryDays(5 + (int) (Math.abs(id % 11))); // 5 ~ 15 天
            q.setQuoteTime(LocalDateTime.now());
            quotes.add(q);
        }
        return quotes;
    }

    private BigDecimal historicalAvgPrice(String materialName) {
        List<Order> orders = orderService.list().stream()
                .filter(o -> o.getMaterialName() != null && o.getMaterialName().contains(materialName))
                .filter(o -> o.getUnitPrice() != null)
                .toList();
        if (orders.isEmpty()) {
            return null;
        }
        double avg = orders.stream().mapToDouble(o -> o.getUnitPrice().doubleValue()).average().orElse(0);
        return BigDecimal.valueOf(avg);
    }

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
