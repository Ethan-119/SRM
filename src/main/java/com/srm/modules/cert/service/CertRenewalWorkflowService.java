package com.srm.modules.cert.service;

import com.srm.modules.graph.repository.Neo4jSupplierRepository;
import com.srm.modules.graph.service.Neo4jSyncService;
import com.srm.modules.graph.vo.CertExpiryAlert;
import com.srm.modules.notification.service.NotificationService;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 供应商资质到期自动续期工作流。
 *
 * 定时扫描即将到期资质 → 通知供应商 → 供应商上传新资质 → AI 自动审核
 * → 写 PG 并同步图谱 → 到期未续期自动冻结。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertRenewalWorkflowService {

    private final Neo4jSupplierRepository neo4jRepo;
    private final ChatClient chatClient;
    private final SupplierService supplierService;
    private final NotificationService notificationService;
    private final SupplierCertService supplierCertService;
    private final Neo4jSyncService neo4jSyncService;

    /** 供应商状态：冻结 */
    private static final int STATUS_FROZEN = 4;
    /** 供应商状态：合作中 */
    private static final int STATUS_ACTIVE = 3;

    /**
     * 触发时机：每天凌晨 2:00（cron {@code 0 0 2 * * ?}）自动执行。
     * 触发条件：Neo4j 中资质 {@code expire_date} 落在未来 30 天内。
     * 动作：通知对应供应商尽快上传新资质。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scanExpiringCerts() {
        List<CertExpiryAlert> alerts = neo4jRepo.findCertsExpiringInDays(30);
        for (CertExpiryAlert alert : alerts) {
            notificationService.notifySupplier(alert.getSupplierId(),
                    "您的「" + alert.getCertType() + "」资质（编号 " + alert.getCertNo()
                            + "）将于 " + alert.getExpireDate() + " 到期，请尽快上传新资质。");
            log.info("资质到期提醒: 供应商 {} 的 {} 将于 {} 到期",
                    alert.getSupplierName(), alert.getCertType(), alert.getExpireDate());
        }
    }

    /**
     * 触发时机：供应商上传新资质时手动触发（{@code POST /api/cert/review}）。
     * 动作：基础规则 + LLM 审核 → 通过则先写 PG（srm_supplier_cert 权威源），
     * 再同步到 Neo4j；无关联风险恢复合作状态，有风险则通知管理员。
     */
    public void autoReviewCert(Long supplierId, String certType, String certNo, LocalDate newExpireDate) {
        boolean valid = aiReviewCert(certType, certNo, newExpireDate);
        if (!valid) {
            notificationService.notifySupplier(supplierId, "资质审核未通过，请重新上传清晰的资质扫描件。");
            return;
        }

        // 先写 PG 权威源，再同步到 Neo4j，恢复「PG 为准」
        supplierCertService.upsert(supplierId, certType, certNo, newExpireDate);
        neo4jSyncService.syncCert();

        List<String> risks = neo4jRepo.getRelatedRisks(supplierId);
        if (risks.isEmpty()) {
            supplierService.updateById(withStatus(supplierId, STATUS_ACTIVE));
            notificationService.notifySupplier(supplierId, "资质续期审核通过，已恢复合作状态。");
        } else {
            notificationService.notifyManager("供应商 " + supplierId + " 资质续期后发现新的关联风险："
                    + String.join("、", risks));
        }
        log.info("资质审核完成: 供应商 {} 的 {} 更新至 {}", supplierId, certType, newExpireDate);
    }

    /**
     * 触发时机：每天凌晨 2:30（cron {@code 0 30 2 * * ?}）自动执行。
     * 触发条件：Neo4j 中资质 {@code expire_date} 已早于今天（过期）。
     * 动作：供应商状态置为 4（冻结）。
     */
    @Scheduled(cron = "0 30 2 * * ?")
    public void freezeExpiredSuppliers() {
        List<Long> expiredIds = neo4jRepo.findSuppliersWithExpiredCerts();
        for (Long supplierId : expiredIds) {
            supplierService.updateById(withStatus(supplierId, STATUS_FROZEN));
            log.warn("供应商 {} 资质已过期，自动冻结", supplierId);
        }
    }

    /** AI 审核资质（基础规则 + 大模型）。 */
    private boolean aiReviewCert(String certType, String certNo, LocalDate newExpireDate) {
        boolean basicOk = certType != null && !certType.isBlank()
                && certNo != null && !certNo.isBlank()
                && newExpireDate != null && newExpireDate.isAfter(LocalDate.now());
        if (!basicOk) {
            return false;
        }
        try {
            String prompt = String.format("""
                    请审核以下资质续期信息：
                    证书类型：%s
                    证书编号：%s
                    新有效期：%s
                    请判断：1) 证书编号格式是否合理 2) 新有效期是否晚于今天 3) 是否存在明显异常
                    只返回 true 或 false
                    """, certType, certNo, newExpireDate);
            String result = chatClient.prompt().user(prompt).call().content();
            return result != null && result.trim().toLowerCase().contains("true");
        } catch (Exception e) {
            log.warn("AI 资质审核调用失败，按基础规则通过", e);
            return true;
        }
    }

    private Supplier withStatus(Long supplierId, int status) {
        Supplier s = new Supplier();
        s.setId(supplierId);
        s.setStatus(status);
        return s;
    }
}
