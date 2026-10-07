package com.srm.modules.rfq.service;

import com.srm.modules.rfq.vo.SupplierScore;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 供应商综合评分：价格 + 交期 + 质量（资质等级）。
 * 价格/交期以所有报价中的最优值为基准归一化。
 */
@Service
@RequiredArgsConstructor
public class SupplierScoringService {

    private final SupplierService supplierService;

    private static final double PRICE_WEIGHT = 0.50;
    private static final double DELIVERY_WEIGHT = 0.20;
    private static final double QUALITY_WEIGHT = 0.30;

    public SupplierScore calculate(Long supplierId, BigDecimal price, int deliveryDays,
                                   BigDecimal minPrice, int minDeliveryDays) {
        Supplier supplier = supplierService.getById(supplierId);
        int qual = (supplier != null && supplier.getQualificationLevel() != null)
                ? supplier.getQualificationLevel() : 1;

        double priceScore = (minPrice != null && minPrice.signum() > 0)
                ? minPrice.divide(price, 6, RoundingMode.HALF_UP).doubleValue() * 100 : 0;
        double deliveryScore = (minDeliveryDays > 0 && deliveryDays > 0)
                ? (double) minDeliveryDays / deliveryDays * 100 : 0;
        double qualityScore = qual * 100.0 / 3.0;

        double total = priceScore * PRICE_WEIGHT + deliveryScore * DELIVERY_WEIGHT
                + qualityScore * QUALITY_WEIGHT;
        return new SupplierScore(supplierId, (int) Math.round(total),
                round1(priceScore), round1(deliveryScore), round1(qualityScore));
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
