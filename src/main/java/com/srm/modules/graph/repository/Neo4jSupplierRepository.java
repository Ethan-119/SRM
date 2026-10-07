package com.srm.modules.graph.repository;

import com.srm.modules.graph.vo.CertExpiryAlert;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 供应商图谱查询仓储：关联风险、集中度、资质到期。
 */
@Repository
@RequiredArgsConstructor
public class Neo4jSupplierRepository {

    private final GraphRepository graphRepository;

    /** 查询供应商的关联企业名称（股权/股东/高管多层穿透）。 */
    public List<String> getRelatedRisks(Long supplierId) {
        String cypher = """
                MATCH (s:Supplier {id: $supplierId})
                  -[:SUBSIDIARY_OF|HAS_SHAREHOLDER|SHARES_EXECUTIVE_WITH*1..3]-(rel:Supplier)
                WHERE rel <> s
                RETURN DISTINCT rel.name AS name
                """;
        return graphRepository.query(cypher, Map.of("supplierId", supplierId)).stream()
                .map(r -> r.get("name").asString())
                .toList();
    }

    /**
     * 计算供应商集团（自身 + 关联企业）在某物料上的采购额占比。
     * 返回值 0~1，> 0.25 视为集中度过高。
     */
    public Double getConcentrationRisk(Long supplierId, String materialName) {
        String totalCypher = """
                MATCH (m:Material {name: $materialName})<-[sup:SUPPLIES]-(:Supplier)
                RETURN sum(toFloat(sup.price) * toFloat(sup.moq)) AS total
                """;
        List<Record> totalRows = graphRepository.query(totalCypher, Map.of("materialName", materialName));
        double total = (totalRows.isEmpty() || totalRows.get(0).get("total").isNull())
                ? 0 : totalRows.get(0).get("total").asNumber().doubleValue();
        if (total <= 0) {
            return 0.0;
        }

        String groupCypher = """
                MATCH (s:Supplier {id: $supplierId})
                OPTIONAL MATCH (s)-[:SUBSIDIARY_OF|HAS_SHAREHOLDER|SHARES_EXECUTIVE_WITH*1..3]-(rel:Supplier)
                WITH collect(DISTINCT s) + collect(DISTINCT rel) AS grp
                UNWIND grp AS g
                MATCH (g)-[sup:SUPPLIES]->(m:Material {name: $materialName})
                RETURN sum(toFloat(sup.price) * toFloat(sup.moq)) AS groupValue
                """;
        List<Record> groupRows = graphRepository.query(groupCypher,
                Map.of("supplierId", supplierId, "materialName", materialName));
        double groupValue = (groupRows.isEmpty() || groupRows.get(0).get("groupValue").isNull())
                ? 0 : groupRows.get(0).get("groupValue").asNumber().doubleValue();
        return groupValue / total;
    }

    /** 查询 N 天内到期且仍有效的资质证书。 */
    public List<CertExpiryAlert> findCertsExpiringInDays(int days) {
        String today = LocalDate.now().toString();
        String threshold = LocalDate.now().plusDays(days).toString();
        String cypher = """
                MATCH (s:Supplier)-[:CERTIFIED_WITH]->(c:Cert)
                WHERE c.expireDate IS NOT NULL
                  AND c.expireDate >= $today AND c.expireDate <= $threshold
                  AND c.status = '有效'
                RETURN s.id AS supplierId, s.name AS supplierName,
                       c.type AS certType, c.certNo AS certNo, c.expireDate AS expireDate
                """;
        return graphRepository.query(cypher, Map.of("today", today, "threshold", threshold)).stream()
                .map(r -> {
                    CertExpiryAlert alert = new CertExpiryAlert();
                    alert.setSupplierId(r.get("supplierId").asLong());
                    alert.setSupplierName(r.get("supplierName").asString());
                    alert.setCertType(r.get("certType").asString());
                    alert.setCertNo(r.get("certNo").asString());
                    alert.setExpireDate(r.get("expireDate").asString());
                    return alert;
                }).toList();
    }

    /** 更新供应商某类资质的新有效期并恢复为有效状态。 */
    public void updateCertExpiry(Long supplierId, String certType, LocalDate newExpireDate) {
        String cypher = """
                MATCH (s:Supplier {id: $supplierId})-[:CERTIFIED_WITH]->(c:Cert {type: $certType})
                SET c.expireDate = $expireDate, c.status = '有效'
                """;
        graphRepository.write(cypher, Map.of(
                "supplierId", supplierId,
                "certType", certType,
                "expireDate", newExpireDate.toString()));
    }

    /** 查询存在已过期资质证书的供应商 ID。 */
    public List<Long> findSuppliersWithExpiredCerts() {
        String today = LocalDate.now().toString();
        String cypher = """
                MATCH (s:Supplier)-[:CERTIFIED_WITH]->(c:Cert)
                WHERE c.expireDate IS NOT NULL AND c.expireDate < $today
                RETURN DISTINCT s.id AS supplierId
                """;
        return graphRepository.query(cypher, Map.of("today", today)).stream()
                .map(r -> r.get("supplierId").asLong())
                .toList();
    }
}
