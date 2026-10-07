package com.srm.modules.rag.eval;

import com.srm.modules.rag.eval.model.EvalCase;
import com.srm.modules.rag.eval.model.EvalReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAG 检索质量集成测试。
 *
 * 依赖：Neo4j 已加载图谱数据、pgvector 已灌入文档向量、DASHSCOPE_API_KEY 已配置。
 * 运行前先执行 graph/schema.cypher + seed.cypher 并完成向量灌库。
 */
@SpringBootTest
class RagEvalTest {

    private static final int K = 5;

    @Autowired
    private RagEvalService ragEvalService;

    @Autowired
    private RagEvalRunner ragEvalRunner;

    @Test
    void goldenDatasetIsLoaded() {
        List<EvalCase> cases = ragEvalService.loadGoldenDataset();
        assertFalse(cases.isEmpty(), "黄金标准数据集不能为空");
        assertTrue(cases.stream().allMatch(c -> c.getId() != null && c.getQuestion() != null));
    }

    @Test
    void runRetrievalEvaluation() {
        List<EvalCase> cases = ragEvalService.loadGoldenDataset();
        EvalReport report = ragEvalRunner.run(cases, K);

        assertNotNull(report);
        assertEquals(cases.size(), report.getTotalCases());
        assertEquals(cases.size(), report.getResults().size());

        // 指标取值合法性校验（0~1）
        assertTrue(report.getAvgRecallAtK() >= 0 && report.getAvgRecallAtK() <= 1);
        assertTrue(report.getAvgPrecisionAtK() >= 0 && report.getAvgPrecisionAtK() <= 1);
        assertTrue(report.getMrr() >= 0 && report.getMrr() <= 1);
        assertTrue(report.getAvgNdcgAtK() >= 0 && report.getAvgNdcgAtK() <= 1);
        assertTrue(report.getLatencyP50() >= 0);

        System.out.println("[RagEval] total=" + report.getTotalCases()
                + " Recall@" + K + "=" + report.getAvgRecallAtK()
                + " Precision@" + K + "=" + report.getAvgPrecisionAtK()
                + " MRR=" + report.getMrr()
                + " NDCG@" + K + "=" + report.getAvgNdcgAtK()
                + " P50=" + report.getLatencyP50() + "ms");
    }
}
