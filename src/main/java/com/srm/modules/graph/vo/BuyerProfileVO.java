package com.srm.modules.graph.vo;

import lombok.Data;

import java.util.List;

/**
 * 采购员画像结果。
 */
@Data
public class BuyerProfileVO {

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String userName;

    /** 供应商合作网络 */
    private List<SupplierNetworkItem> supplierNetwork;

    /** 品类覆盖与偏好 */
    private List<CategoryItem> categoryCoverage;

    /** 议价能力评估 */
    private List<BargainItem> bargaining;

    @Data
    public static class SupplierNetworkItem {
        private String supplierName;
        private Long orderCount;
        private Double totalAmount;
    }

    @Data
    public static class CategoryItem {
        private String category;
        private String material;
        private Long count;
    }

    @Data
    public static class BargainItem {
        private String materialName;
        private Double myPrice;
        private Double marketPrice;
        /** 节约率：(市场价-我的价)/市场价，正数表示议价优于市场 */
        private Double savingRate;
    }
}
