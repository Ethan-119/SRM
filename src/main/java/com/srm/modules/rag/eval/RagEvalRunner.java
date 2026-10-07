package com.srm.modules.rag.eval;

import com.srm.modules.rag.eval.model.EvalCase;
import com.srm.modules.rag.eval.model.EvalReport;
import com.srm.modules.rag.eval.model.EvalResult;
import com.srm.modules.rag.service.HybridRagService;
import com.srm.modules.rag.vo.VectorDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * 批量评估运行器：遍历黄金标准数据集，计算检索指标并聚合报告。
 */
@Service
@RequiredArgsConstructor
public class RagEvalRunner {

    private final HybridRagService hybridRagService;

    public EvalReport run(List<EvalCase> cases, int k) {
        List<EvalResult> results = new ArrayList<>(cases.size());
        for (EvalCase c : cases) {
            results.add(evaluate(c, k));
        }
        return buildReport(results);
    }

    private EvalResult evaluate(EvalCase c, int k) {
        long start = System.nanoTime();
        List<VectorDocument> docs = hybridRagService.retrieve(c.getQuestion(), k);
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        List<String> retrievedIds = docs.stream().map(VectorDocument::id).toList();
        Set<String> expected = new HashSet<>(c.getExpectedDocIds());

        EvalResult r = new EvalResult();
        r.setCaseId(c.getId());
        r.setQuestion(c.getQuestion());
        r.setRetrievedDocIds(retrievedIds);
        r.setRecallAtK(round(RagMetricsCalculator.recallAtK(retrievedIds, expected, k)));
        r.setPrecisionAtK(round(RagMetricsCalculator.precisionAtK(retrievedIds, expected, k)));
        r.setReciprocalRank(round(RagMetricsCalculator.reciprocalRank(retrievedIds, expected)));
        r.setNdcgAtK(round(RagMetricsCalculator.ndcgAtK(retrievedIds, expected, k)));
        r.setLatencyMs(latencyMs);
        r.setTags(c.getTags());
        r.setDifficulty(c.getDifficulty());
        return r;
    }

    private EvalReport buildReport(List<EvalResult> results) {
        EvalReport report = new EvalReport();
        report.setTotalCases(results.size());
        report.setAvgRecallAtK(round(avg(results, EvalResult::getRecallAtK)));
        report.setAvgPrecisionAtK(round(avg(results, EvalResult::getPrecisionAtK)));
        report.setMrr(round(avg(results, EvalResult::getReciprocalRank)));
        report.setAvgNdcgAtK(round(avg(results, EvalResult::getNdcgAtK)));
        report.setLatencyP50(percentile(results, 50));
        report.setLatencyP95(percentile(results, 95));
        report.setLatencyP99(percentile(results, 99));

        double faithSum = 0;
        int faithCount = 0;
        for (EvalResult r : results) {
            if (r.getFaithfulness() != null) {
                faithSum += r.getFaithfulness();
                faithCount++;
            }
        }
        report.setAvgFaithfulness(faithCount > 0 ? round(faithSum / faithCount) : null);
        report.setByTag(groupByTag(results));
        report.setResults(results);
        return report;
    }

    private Map<String, EvalReport.TagStats> groupByTag(List<EvalResult> results) {
        Map<String, List<EvalResult>> grouped = new HashMap<>();
        for (EvalResult r : results) {
            if (r.getTags() == null) {
                continue;
            }
            for (String tag : r.getTags()) {
                grouped.computeIfAbsent(tag, k -> new ArrayList<>()).add(r);
            }
        }
        Map<String, EvalReport.TagStats> stats = new LinkedHashMap<>();
        grouped.forEach((tag, list) -> {
            EvalReport.TagStats ts = new EvalReport.TagStats();
            ts.setCount(list.size());
            ts.setAvgRecallAtK(round(avg(list, EvalResult::getRecallAtK)));
            ts.setAvgPrecisionAtK(round(avg(list, EvalResult::getPrecisionAtK)));
            ts.setMrr(round(avg(list, EvalResult::getReciprocalRank)));
            ts.setAvgNdcgAtK(round(avg(list, EvalResult::getNdcgAtK)));
            stats.put(tag, ts);
        });
        return stats;
    }

    private double avg(List<EvalResult> results, ToDoubleFunction<EvalResult> fn) {
        if (results.isEmpty()) {
            return 0.0;
        }
        double sum = 0;
        for (EvalResult r : results) {
            sum += fn.applyAsDouble(r);
        }
        return sum / results.size();
    }

    private double percentile(List<EvalResult> results, int p) {
        if (results.isEmpty()) {
            return 0.0;
        }
        List<Long> latencies = results.stream()
                .map(EvalResult::getLatencyMs)
                .sorted()
                .toList();
        int n = latencies.size();
        int idx = Math.min(n - 1, (int) Math.ceil(p / 100.0 * n) - 1);
        idx = Math.max(idx, 0);
        return latencies.get(idx);
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
