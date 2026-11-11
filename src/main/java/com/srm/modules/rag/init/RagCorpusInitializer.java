package com.srm.modules.rag.init;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.srm.modules.cert.entity.SupplierCert;
import com.srm.modules.cert.mapper.SupplierCertMapper;
import com.srm.modules.graph.repository.GraphRepository;
import com.srm.modules.rag.entity.RagDocument;
import com.srm.modules.rag.mapper.RagDocumentMapper;
import com.srm.modules.rag.service.PgVectorStoreService;
import com.srm.modules.rag.service.RagDocumentService;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.mapper.SupplierMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Record;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 真实语料灌库器：用真实业务数据（供应商 / 资质证书 / 物料供应）与制度文档重建知识库。
 *
 * <p>仅在 {@code srm.rag.ingest-on-startup=true} 时启动执行。执行前先清空旧语料
 * （{@code rag_document} + {@code vector_store}），再按固定 ID 规则灌入：</p>
 * <ul>
 *   <li>{@code supplier-{id}}：供应商档案（200 条，来自 srm_supplier）</li>
 *   <li>{@code cert-{certNo}}：资质证书（20 条，来自 srm_supplier_cert）</li>
 *   <li>{@code supply-{supplierId}-{materialId}}：物料供应（来自图谱 SUPPLIES）</li>
 *   <li>{@code policy-*}：制度/流程文档（classpath:rag/corpus/*.md）</li>
 * </ul>
 *
 * <p>文档正文中的供应商名与 Neo4j {@code Supplier.name} 完全一致，保证
 * {@code HybridRagService} 的结构化过滤能命中。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "srm.rag.ingest-on-startup", havingValue = "true")
public class RagCorpusInitializer implements CommandLineRunner {

    private static final String[] STATUS_NAMES = {"注册", "待审核", "已准入", "合作中", "冻结", "黑名单"};
    private static final String[] QUAL_NAMES = {null, "初级", "中级", "高级"};

    private final SupplierMapper supplierMapper;
    private final SupplierCertMapper supplierCertMapper;
    private final GraphRepository graphRepository;
    private final RagDocumentMapper ragDocumentMapper;
    private final PgVectorStoreService pgVectorStoreService;
    private final RagDocumentService ragDocumentService;

    @Override
    public void run(String... args) {
        clearOldCorpus();
        int supplierCount = ingestSuppliers();
        int certCount = ingestCerts();
        int supplyCount = ingestSupplies();
        int policyCount = ingestPolicies();
        log.info("RAG 真实语料灌库完成：供应商 {} 条、证书 {} 条、物料供应 {} 条、制度 {} 篇",
                supplierCount, certCount, supplyCount, policyCount);
    }

    private void clearOldCorpus() {
        // 注意：BlockAttackInnerInterceptor 拦截无条件 DELETE，需显式带 WHERE 才允许全表清空
        ragDocumentMapper.delete(new QueryWrapper<RagDocument>().isNotNull("id"));
        pgVectorStoreService.clearAll();
        log.info("已清空旧语料（rag_document + vector_store）");
    }

    private int ingestSuppliers() {
        List<Supplier> suppliers = supplierMapper.selectList(null);
        for (Supplier s : suppliers) {
            String content = "供应商：" + s.getSupplierName()
                    + "（供应商编码 " + s.getSupplierCode() + "），位于" + nvl(s.getRegion())
                    + "，主营品类：" + nvl(s.getMainCategory())
                    + "，资质等级：" + qualName(s.getQualificationLevel())
                    + "，当前状态：" + statusName(s.getStatus())
                    + "，地址：" + nvl(s.getAddress()) + "。";
            ragDocumentService.add("supplier-" + s.getId(), s.getSupplierName(), content, "supplier", null);
        }
        return suppliers.size();
    }

    private int ingestCerts() {
        Map<Long, String> nameMap = supplierMapper.selectList(null).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getSupplierName));
        List<SupplierCert> certs = supplierCertMapper.selectList(null);
        for (SupplierCert c : certs) {
            String supplierName = nameMap.getOrDefault(c.getSupplierId(), "未知供应商");
            String content = "供应商 " + supplierName + " 持有 " + c.getCertType()
                    + " 证书（证书编号 " + c.getCertNo() + "），有效期至 " + c.getExpireDate()
                    + "，证书状态：" + c.getStatus() + "。";
            ragDocumentService.add("cert-" + c.getCertNo(), c.getCertNo(), content, "cert", null);
        }
        return certs.size();
    }

    private int ingestSupplies() {
        String cypher = "MATCH (s:Supplier)-[r:SUPPLIES]->(m:Material) "
                + "RETURN s.id AS supplierId, s.name AS supplierName, m.id AS materialId, "
                + "m.name AS materialName, m.category AS category, m.unit AS unit, "
                + "r.price AS price, r.moq AS moq, r.leadTime AS leadTime";
        List<Record> records = graphRepository.query(cypher);
        for (Record rec : records) {
            long supplierId = rec.get("supplierId").asLong();
            String supplierName = rec.get("supplierName").asString();
            String materialId = rec.get("materialId").asString();
            String materialName = rec.get("materialName").asString();
            String category = rec.get("category").asString();
            String unit = rec.get("unit").asString();
            double price = rec.get("price").asDouble();
            int moq = rec.get("moq").asInt();
            int leadTime = rec.get("leadTime").asInt();
            String content = "供应商 " + supplierName + " 供应物料 " + materialName
                    + "（品类：" + category + "，单位：" + unit + "），单价 " + price
                    + " 元，最小起订量 " + moq + "，交货期 " + leadTime + " 天。";
            ragDocumentService.add("supply-" + supplierId + "-" + materialId,
                    materialName, content, "supply", null);
        }
        return records.size();
    }

    private int ingestPolicies() {
        ingestPolicy("policy-admission", "供应商准入管理制度（现行版）");
        ingestPolicy("policy-admission-v1", "供应商准入管理制度（旧版）");
        ingestPolicy("policy-procurement", "SRM 采购流程说明");
        return 3;
    }

    private void ingestPolicy(String id, String title) {
        String content = readResource("rag/corpus/" + id + ".md");
        ragDocumentService.add(id, title, content, "policy", null);
    }

    private String readResource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取语料资源失败: " + path, e);
        }
    }

    private String statusName(Integer code) {
        return code != null && code >= 0 && code < STATUS_NAMES.length ? STATUS_NAMES[code] : "未知";
    }

    private String qualName(Integer level) {
        return level != null && level >= 1 && level < QUAL_NAMES.length ? QUAL_NAMES[level] : "未知";
    }

    private String nvl(String s) {
        return s == null || s.isBlank() ? "未知" : s;
    }
}
