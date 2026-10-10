package com.srm.modules.rfq.controller;

import cn.hutool.json.JSONUtil;
import com.srm.common.Result;
import com.srm.modules.rfq.dto.RfqConfirmDTO;
import com.srm.modules.rfq.entity.RfqWorkflow;
import com.srm.modules.rfq.service.RfqWorkflowService;
import com.srm.modules.rfq.vo.AiAnalysisResult;
import com.srm.modules.rfq.vo.QuoteRecord;
import com.srm.modules.rfq.vo.RfqWorkflowVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 智能询比价。发起/分析由 Agent 工具（{@code RfqTool}）驱动，这里只保留确认与查询。
 */
@Tag(name = "智能询比价")
@RestController
@RequestMapping("/api/rfq")
@RequiredArgsConstructor
public class RfqWorkflowController {

    private final RfqWorkflowService rfqService;

    @Operation(summary = "采购员确认/驳回")
    @PostMapping("/{id}/confirm")
    public Result<Long> confirm(@PathVariable Long id, @RequestBody RfqConfirmDTO req) {
        Long orderId = rfqService.confirmRfq(id, req.getManagerId(), req.isApproved(), req.getComment());
        return Result.ok(orderId);
    }

    @Operation(summary = "查询询比价单详情")
    @GetMapping("/{id}")
    public Result<RfqWorkflowVO> get(@PathVariable Long id) {
        return Result.ok(toVO(rfqService.getById(id)));
    }

    @Operation(summary = "查询询比价单列表")
    @GetMapping
    public Result<List<RfqWorkflowVO>> list() {
        return Result.ok(rfqService.list().stream().map(this::toVO).toList());
    }

    private RfqWorkflowVO toVO(RfqWorkflow rfq) {
        if (rfq == null) {
            return null;
        }
        RfqWorkflowVO vo = new RfqWorkflowVO();
        vo.setId(rfq.getId());
        vo.setRfqNo(rfq.getRfqNo());
        vo.setMaterialName(rfq.getMaterialName());
        vo.setQuantity(rfq.getQuantity());
        vo.setSupplierIds(rfq.getSupplierIds() == null ? List.of()
                : JSONUtil.toList(rfq.getSupplierIds(), Long.class));
        vo.setQuotes(rfq.getQuotes() == null ? List.of()
                : JSONUtil.toList(rfq.getQuotes(), QuoteRecord.class));
        vo.setAiAnalysis(rfq.getAiAnalysis() == null ? null
                : JSONUtil.toBean(rfq.getAiAnalysis(), AiAnalysisResult.class));
        vo.setStatus(rfq.getStatus());
        vo.setQuoteDeadline(rfq.getQuoteDeadline());
        vo.setConfirmBy(rfq.getConfirmBy());
        vo.setConfirmTime(rfq.getConfirmTime());
        vo.setOrderId(rfq.getOrderId());
        vo.setCreateTime(rfq.getCreateTime());
        return vo;
    }
}
