package com.srm.modules.agent.tools;

import com.srm.modules.order.entity.Order;
import com.srm.modules.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 价格相关工具（轻量快速查询）。
 * 正式询比价/比价/推荐已收敛到 {@code RfqTool.runFormalRfq}。
 */
@Component
@RequiredArgsConstructor
public class PriceTools {

    private final OrderService orderService;

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
}
