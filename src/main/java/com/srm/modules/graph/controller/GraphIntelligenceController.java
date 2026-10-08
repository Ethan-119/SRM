package com.srm.modules.graph.controller;

import com.srm.common.Result;
import com.srm.modules.graph.service.GraphIntelligenceService;
import com.srm.modules.graph.service.Neo4jSyncService;
import com.srm.modules.graph.service.SupplierScoreService;
import com.srm.modules.graph.vo.AlternativeSupplierVO;
import com.srm.modules.graph.vo.BuyerProfileVO;
import com.srm.modules.graph.vo.ConcentrationRiskVO;
import com.srm.modules.graph.vo.RelationRiskVO;
import com.srm.modules.graph.vo.SupplierScoreVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供应链智能分析接口（基于 Neo4j 知识图谱）。
 */
@Tag(name = "供应链智能分析")
@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphIntelligenceController {

    private final GraphIntelligenceService graphIntelligenceService;
    private final SupplierScoreService supplierScoreService;
    private final Neo4jSyncService neo4jSyncService;

    @Operation(summary = "供应链集中度风险（赫芬达尔指数）")
    @GetMapping("/concentration")
    public Result<List<ConcentrationRiskVO>> concentration(@RequestParam String materialName) {
        return Result.ok(graphIntelligenceService.analyzeConcentration(materialName));
    }

    @Operation(summary = "替代供应商发现")
    @GetMapping("/alternative/{supplierId}")
    public Result<List<AlternativeSupplierVO>> alternative(@PathVariable Long supplierId) {
        return Result.ok(graphIntelligenceService.findAlternativeSuppliers(supplierId));
    }

    @Operation(summary = "关联风险穿透（多层）")
    @GetMapping("/relation-risk/{supplierId}")
    public Result<List<RelationRiskVO>> relationRisk(@PathVariable Long supplierId,
                                                     @RequestParam(defaultValue = "3") int maxDepth) {
        return Result.ok(graphIntelligenceService.penetrateRelationRisk(supplierId, maxDepth));
    }

    @Operation(summary = "供应商综合评分")
    @GetMapping("/supplier-score/{supplierId}")
    public Result<SupplierScoreVO> supplierScore(@PathVariable Long supplierId) {
        return Result.ok(supplierScoreService.score(supplierId));
    }

    @Operation(summary = "采购员画像")
    @GetMapping("/buyer-profile/{userId}")
    public Result<BuyerProfileVO> buyerProfile(@PathVariable Long userId) {
        return Result.ok(graphIntelligenceService.buildBuyerProfile(userId));
    }

    @Operation(summary = "同步业务库数据到 Neo4j 图谱")
    @PostMapping("/sync")
    public Result<java.util.Map<String, Integer>> sync() {
        return Result.ok(neo4jSyncService.syncAll());
    }
}
