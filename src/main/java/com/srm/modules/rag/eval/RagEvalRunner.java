package com.srm.modules.rag.eval;

import com.srm.modules.rag.eval.model.EvalCase;
import com.srm.modules.rag.eval.model.EvalReport;
import com.srm.modules.rag.eval.model.EvalResult;
import com.srm.modules.rag.service.HybridRagService;
import com.srm.modules.rag.vo.VectorDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * RAG 批量评估运行器。
 *
 * <p>遍历黄金标准数据集，对每条用例计算两类指标并聚合为 {@link EvalReport}：
 * <ul>
 *   <li>检索指标：Recall@K / Precision@K / MRR / NDCG@K（{@link RagMetricsCalculator}）；</li>
 *   <li>生成指标：答案准确率（关键词命中率）+ 忠实度（{@link LlmJudgeService} LLM 裁判）。</li>
 * </ul>
 * 同时统计检索延迟 P50/P95/P99 与按标签分组指标。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEvalRunner {

    private final HybridRagService hybridRagService;
    private final ChatClient chatClient;
    private final LlmJudgeService llmJudgeService;

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

        // 生成一次答案，供准确率 + 忠实度共用，避免重复调用 LLM
        String answer = generateAnswerSafe(c.getQuestion(), docs);

        EvalResult r = new EvalResult();
        r.setCaseId(c.getId());
        r.setQuestion(c.getQuestion());
        r.setRetrievedDocIds(retrievedIds);
        r.setRecallAtK(round(RagMetricsCalculator.recallAtK(retrievedIds, expected, k)));
        r.setPrecisionAtK(round(RagMetricsCalculator.precisionAtK(retrievedIds, expected, k)));
        r.setReciprocalRank(round(RagMetricsCalculator.reciprocalRank(retrievedIds, expected)));
        r.setNdcgAtK(round(RagMetricsCalculator.ndcgAtK(retrievedIds, expected, k)));
        r.setAccuracy(round(computeAccuracy(answer, c.getExpectedAnswerContains())));
        r.setFaithfulness(round(computeFaithfulness(c, docs, answer)));
        r.setLatencyMs(latencyMs);
        r.setTags(c.getTags());
        r.setDifficulty(c.getDifficulty());
        return r;
    }

    /** 用检索到的资料生成答案；失败返回 null（准确率/忠实度会随之判 0）。 */
    private String generateAnswerSafe(String question, List<VectorDocument> docs) {
        try {
            return generateAnswer(question, docs);
        } catch (Exception e) {
            log.warn("答案生成失败 question={}", question, e);
            return null;
        }
    }

    /** 答案准确率：命中 expectedAnswerContains 关键词的比例（0~1）。 */
    private double computeAccuracy(String answer, List<String> keywords) {
        if (answer == null || keywords == null || keywords.isEmpty()) {
            return 0.0;
        }
        int hit = 0;
        for (String kw : keywords) {
            if (answer.contains(kw)) {
                hit++;
            }
        }
        return (double) hit / keywords.size();
    }

    /** 忠实度：LLM 裁判对比「回答 vs 检索上下文」打分（0~1）；失败判 0。 */
    private double computeFaithfulness(EvalCase c, List<VectorDocument> docs, String answer) {
        if (answer == null) {
            return 0.0;
        }
        try {
            String context = docs.stream().map(VectorDocument::content)
                    .collect(Collectors.joining("\n"));
            return llmJudgeService.judgeFaithfulness(c.getQuestion(), answer, context);
        } catch (Exception e) {
            log.warn("忠实度评估失败 caseId={}", c.getId(), e);
            return 0.0;
        }
    }

    private String generateAnswer(String question, List<VectorDocument> docs) {
        String context = docs.stream().map(VectorDocument::content)
                .collect(Collectors.joining("\n"));
        return chatClient.prompt()
                .system("你是采购知识库助手。请仅依据下面提供的资料回答问题，不要编造资料中没有的事实。")
                .user("问题：" + question + "\n资料：\n" + context)
                .call()
                .content();
    }

    private EvalReport buildReport(List<EvalResult> results) {
        EvalReport report = new EvalReport();
        report.setTotalCases(results.size());
        report.setAvgRecallAtK(round(avg(results, EvalResult::getRecallAtK)));
        report.setAvgPrecisionAtK(round(avg(results, EvalResult::getPrecisionAtK)));
        report.setMrr(round(avg(results, EvalResult::getReciprocalRank)));
        report.setAvgNdcgAtK(round(avg(results, EvalResult::getNdcgAtK)));
        report.setAvgAccuracy(round(avg(results, EvalResult::getAccuracy)));
        report.setAvgFaithfulness(round(avg(results, EvalResult::getFaithfulness)));
        report.setLatencyP50(percentile(results, 50));
        report.setLatencyP95(percentile(results, 95));
        report.setLatencyP99(percentile(results, 99));
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
