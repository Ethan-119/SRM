package com.srm.modules.rfq.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 询比价单返回对象（JSON 字段已解析为对象）。
 */
@Data
public class RfqWorkflowVO {

    private Long id;
    private String rfqNo;
    private String materialName;
    private BigDecimal quantity;
    private List<Long> supplierIds;
    private List<QuoteRecord> quotes;
    private AiAnalysisResult aiAnalysis;
    private String status;
    private LocalDateTime quoteDeadline;
    private Long confirmBy;
    private LocalDateTime confirmTime;
    private Long orderId;
    private LocalDateTime createTime;
}
