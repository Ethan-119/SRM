package com.srm.modules.rag.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 检索指标计算器单元测试（无外部依赖）。
 */
class RagMetricsCalculatorTest {

    @Test
    void recallAndPrecision() {
        List<String> retrieved = List.of("a", "b", "c", "d", "e");
        Set<String> expected = Set.of("a", "c", "x");
        assertEquals(2.0 / 3.0, RagMetricsCalculator.recallAtK(retrieved, expected, 5), 1e-9);
        assertEquals(2.0 / 5.0, RagMetricsCalculator.precisionAtK(retrieved, expected, 5), 1e-9);
    }

    @Test
    void reciprocalRank() {
        Set<String> expected = Set.of("b", "z");
        // 首个命中在第 2 位（下标 1）-> RR = 1/2
        assertEquals(0.5, RagMetricsCalculator.reciprocalRank(List.of("a", "b", "c"), expected), 1e-9);
        assertEquals(0.0, RagMetricsCalculator.reciprocalRank(List.of("a", "c"), expected), 1e-9);
    }

    @Test
    void ndcg() {
        // 理想顺序：前 3 位全命中
        List<String> retrieved = List.of("x", "a", "b");
        Set<String> expected = Set.of("a", "b", "c");
        double ndcg = RagMetricsCalculator.ndcgAtK(retrieved, expected, 3);
        assertEquals(0.5, ndcg, 1e-9); // 命中第 2、3 位
    }

    @Test
    void emptyExpected() {
        assertEquals(0.0, RagMetricsCalculator.recallAtK(List.of("a"), Set.of(), 5), 1e-9);
        assertEquals(0.0, RagMetricsCalculator.ndcgAtK(List.of("a"), Set.of(), 5), 1e-9);
    }
}
