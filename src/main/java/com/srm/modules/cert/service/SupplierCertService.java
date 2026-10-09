package com.srm.modules.cert.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.srm.modules.cert.entity.SupplierCert;
import com.srm.modules.cert.mapper.SupplierCertMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 供应商资质证书 CRUD（PG 权威数据源）。
 */
@Service
@RequiredArgsConstructor
public class SupplierCertService {

    private final SupplierCertMapper supplierCertMapper;

    /** 查询某供应商的全部证书，按过期日期倒序。 */
    public List<SupplierCert> listBySupplier(Long supplierId) {
        return supplierCertMapper.selectList(
                new LambdaQueryWrapper<SupplierCert>()
                        .eq(SupplierCert::getSupplierId, supplierId)
                        .orderByDesc(SupplierCert::getExpireDate));
    }

    /** 按 supplierId + certType 定位，upsert 一条证书。 */
    public void upsert(Long supplierId, String certType, String certNo, LocalDate expireDate) {
        SupplierCert existing = supplierCertMapper.selectOne(
                new LambdaQueryWrapper<SupplierCert>()
                        .eq(SupplierCert::getSupplierId, supplierId)
                        .eq(SupplierCert::getCertType, certType));
        SupplierCert cert = existing == null ? new SupplierCert() : existing;
        cert.setSupplierId(supplierId);
        cert.setCertType(certType);
        cert.setCertNo(certNo);
        cert.setExpireDate(expireDate);
        cert.setStatus("有效");
        if (cert.getIssueDate() == null) {
            cert.setIssueDate(LocalDate.now());
        }
        if (existing == null) {
            supplierCertMapper.insert(cert);
        } else {
            supplierCertMapper.updateById(cert);
        }
    }
}
