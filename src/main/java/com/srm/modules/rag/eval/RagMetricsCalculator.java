package com.srm.modules.rag.eval;

import java.util.List;
import java.util.Set;

/**
 * RAG 检索指标计算器：Recall@K / Precision@K / MRR / NDCG@K。
 */
public final class RagMetricsCalculator {

    private RagMetricsCalculator() {
    }

    /** Recall@K：前 K 个结果中命中正确答案的比例（相对期望全集）。 */
    public static double recallAtK(List<String> retrieved, Set<String> expected, int k) {
        if (expected.isEmpty()) {
            return 0.0;
        }
        int hits = 0;
        int limit = Math.min(k, retrieved.size());
        for (int i = 0; i < limit; i++) {
            if (expected.contains(retrieved.get(i))) {
                hits++;
            }
        }
        return (double) hits / expected.size();
    }

    /** Precision@K：前 K 个结果中正确的比例。 */
    public static double precisionAtK(List<String> retrieved, Set<String> expected, int k) {
        int limit = Math.min(k, retrieved.size());
        if (limit == 0) {
            return 0.0;
        }
        int hits = 0;
        for (int i = 0; i < limit; i++) {
            if (expected.contains(retrieved.get(i))) {
                hits++;
            }
        }
        return (double) hits / limit;
    }

    /** 倒数排名：1 / 首个命中位置（从 1 开始），无命中返回 0。 */
    public static double reciprocalRank(List<String> retrieved, Set<String> expected) {
        for (int i = 0; i < retrieved.size(); i++) {
            if (expected.contains(retrieved.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /** NDCG@K：二值相关性（命中=1）下的归一化折损累计增益。 */
    public static double ndcgAtK(List<String> retrieved, Set<String> expected, int k) {
        int limit = Math.min(k, retrieved.size());
        double dcg = 0.0;
        for (int i = 0; i < limit; i++) {
            if (expected.contains(retrieved.get(i))) {
                dcg += 1.0 / log2(i + 2);
            }
        }
        int idealCount = Math.min(expected.size(), k);
        double idcg = 0.0;
        for (int i = 0; i < idealCount; i++) {
            idcg += 1.0 / log2(i + 2);
        }
        if (idcg == 0.0) {
            return 0.0;
        }
        return dcg / idcg;
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
