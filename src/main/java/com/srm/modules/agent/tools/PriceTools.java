package com.srm.modules.agent.tools;

import com.srm.modules.order.entity.Order;
import com.srm.modules.order.service.OrderService;
import com.srm.modules.supplier.entity.Supplier;
import com.srm.modules.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 价格与询比价相关工具。
 */
@Component
@RequiredArgsConstructor
public class PriceTools {

    private final OrderService orderService;
    private final SupplierService supplierService;

    @Tool(description = "获取指定物料的历史成交价格参考（平均价/最低价/最高价）")
    public String getPriceReference(@ToolParam(description = "物料名称") String materialName) {
        List<Order> orders = filterByMaterial(materialName);
        if (orders.isEmpty()) {
            return "未找到物料「" + materialName + "」的历史价格数据。";
        }
        double avg = orders.stream().mapToDouble(o -> toDouble(o.getUnitPrice())).average().orElse(0);
        double min = orders.stream().mapToDouble(o -> toDouble(o.getUnitPrice())).min().orElse(0);
        double max = orders.stream().mapToDouble(o -> toDouble(o.getUnitPrice())).max().orElse(0);
        return "物料: " + materialName + "\n"
                + "  历史平均价: " + fmt(avg) + "元\n"
                + "  历史最低价: " + fmt(min) + "元\n"
                + "  历史最高价: " + fmt(max) + "元\n"
                + "  成交记录数: " + orders.size() + "笔";
    }

    @Tool(description = "获取指定物料近 N 个月的价格走势（平均/最低/最高及成交笔数）")
    public String getPriceIntelligence(@ToolParam(description = "物料名称") String materialName,
                                       @ToolParam(description = "近几个月，如 3/6/12", required = false) Integer months) {
        int m = (months == null || months <= 0) ? 6 : months;
        LocalDateTime since = LocalDateTime.now().minusMonths(m);
        List<Order> orders = filterByMaterial(materialName).stream()
                .filter(o -> o.getCreateTime() != null && o.getCreateTime().isAfter(since))
                .toList();
        if (orders.isEmpty()) {
            return "近 " + m + " 个月内未找到物料「" + materialName + "」的成交记录。";
        }
        double avg = orders.stream().mapToDouble(o -> toDouble(o.getUnitPrice())).average().orElse(0);
        double min = orders.stream().mapToDouble(o -> toDouble(o.getUnitPrice())).min().orElse(0);
        double max = orders.stream().mapToDouble(o -> toDouble(o.getUnitPrice())).max().orElse(0);
        return "物料「" + materialName + "」近 " + m + " 个月价格情报：\n"
                + "  平均价: " + fmt(avg) + "元 | 最低价: " + fmt(min)
                + "元 | 最高价: " + fmt(max) + "元 | 成交: " + orders.size() + "笔";
    }

    @Tool(description = "计算采购总成本（含阶梯折扣：>=1000 打 3%，>=5000 打 6%，>=10000 打 10%）")
    public String calculateTotalCost(@ToolParam(description = "单价（元）") double unitPrice,
                                     @ToolParam(description = "数量") int quantity) {
        if (unitPrice <= 0 || quantity <= 0) {
            return "计算失败：单价和数量必须大于 0。";
        }
        double raw = unitPrice * quantity;
        String note;
        double rate;
        if (quantity >= 10000) { rate = 0.10; note = "10%（批量>=10000）"; }
        else if (quantity >= 5000) { rate = 0.06; note = "6%（批量>=5000）"; }
        else if (quantity >= 1000) { rate = 0.03; note = "3%（批量>=1000）"; }
        else { rate = 0.0; note = "无折扣"; }
        double discount = raw * rate;
        double total = raw - discount;
        return "采购成本明细：\n"
                + "  单价: " + fmt(unitPrice) + "元 × 数量: " + quantity + " = 小计: " + fmt(raw) + "元\n"
                + "  阶梯折扣: " + note + ", 折扣金额: " + fmt(discount) + "元\n"
                + "  合计总价: " + fmt(total) + "元";
    }

    @Tool(description = "对指定物料进行供应商报价排名，返回按最优单价升序排列的供应商列表")
    public String getSupplierRanking(@ToolParam(description = "物料名称") String materialName,
                                     @ToolParam(description = "地区过滤，可为空", required = false) String regionFilter) {
        Map<Long, Supplier> supplierMap = supplierService.list().stream()
                .collect(Collectors.toMap(Supplier::getId, s -> s, (a, b) -> a));

        // 供应商 -> 最优单价
        Map<Long, Double> bestPrice = new HashMap<>();
        for (Order o : filterByMaterial(materialName)) {
            Double cur = bestPrice.get(o.getSupplierId());
            double p = toDouble(o.getUnitPrice());
            if (cur == null || p < cur) {
                bestPrice.put(o.getSupplierId(), p);
            }
        }

        List<Map.Entry<Long, Double>> ranked = new ArrayList<>(bestPrice.entrySet());
        ranked.sort(Map.Entry.comparingByValue());

        StringBuilder sb = new StringBuilder("物料「" + materialName + "」供应商报价排名：\n");
        int idx = 0;
        for (Map.Entry<Long, Double> e : ranked) {
            Supplier s = supplierMap.get(e.getKey());
            if (s == null) {
                continue;
            }
            if (regionFilter != null && !regionFilter.isBlank()
                    && !regionFilter.equals(s.getRegion())) {
                continue;
            }
            idx++;
            sb.append(idx).append(". ").append(s.getSupplierName())
                    .append("（").append(s.getRegion()).append("）")
                    .append(" 最优单价: ").append(fmt(e.getValue())).append("元")
                    .append(" 状态: ").append(status(s.getStatus())).append("\n");
        }
        if (idx == 0) {
            return "未找到物料「" + materialName + "」的可比价供应商" +
                    (regionFilter == null || regionFilter.isBlank() ? "" : "（地区：" + regionFilter + "）") + "。";
        }
        return sb.toString();
    }

    @Tool(description = "对指定物料和指定供应商集合生成比价报告")
    public String generateComparisonReport(@ToolParam(description = "物料名称") String materialName,
                                           @ToolParam(description = "供应商ID列表") List<Long> supplierIds) {
        Map<Long, Supplier> supplierMap = supplierService.list().stream()
                .collect(Collectors.toMap(Supplier::getId, s -> s, (a, b) -> a));

        Map<Long, Double> bestPrice = new HashMap<>();
        for (Order o : filterByMaterial(materialName)) {
            if (!supplierIds.contains(o.getSupplierId())) {
                continue;
            }
            double p = toDouble(o.getUnitPrice());
            bestPrice.merge(o.getSupplierId(), p, (a, b) -> a <= b ? a : b);
        }

        StringBuilder sb = new StringBuilder("物料「" + materialName + "」比价报告：\n");
        List<Map.Entry<Long, Double>> ranked = new ArrayList<>(bestPrice.entrySet());
        ranked.sort(Map.Entry.comparingByValue());
        for (Map.Entry<Long, Double> e : ranked) {
            Supplier s = supplierMap.get(e.getKey());
            if (s == null) {
                continue;
            }
            sb.append("- ").append(s.getSupplierName())
                    .append(" 报价: ").append(fmt(e.getValue())).append("元")
                    .append(" 资质: ").append(s.getQualificationLevel())
                    .append(" 状态: ").append(status(s.getStatus())).append("\n");
        }
        if (ranked.isEmpty()) {
            return "指定供应商在物料「" + materialName + "」上暂无成交记录，无法比价。";
        }
        return sb.toString();
    }

    private List<Order> filterByMaterial(String materialName) {
        List<Order> orders = orderService.list();
        return orders.stream()
                .filter(o -> o.getMaterialName() != null && o.getMaterialName().contains(materialName))
                .toList();
    }

    private double toDouble(BigDecimal v) {
        return v == null ? 0.0 : v.doubleValue();
    }

    private String fmt(double v) {
        return String.format("%,.2f", v);
    }

    private String status(Integer s) {
        return switch (s == null ? -1 : s) {
            case 2 -> "已准入";
            case 3 -> "合作中";
            case 4 -> "冻结";
            case 5 -> "黑名单";
            default -> "未知";
        };
    }
}
