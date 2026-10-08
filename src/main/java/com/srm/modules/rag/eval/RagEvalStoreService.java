package com.srm.modules.rag.eval;

import cn.hutool.json.JSONUtil;
import com.srm.modules.rag.entity.RagEvalResultEntity;
import com.srm.modules.rag.entity.RagEvalRun;
import com.srm.modules.rag.eval.model.EvalReport;
import com.srm.modules.rag.eval.model.EvalResult;
import com.srm.modules.rag.mapper.RagEvalResultMapper;
import com.srm.modules.rag.mapper.RagEvalRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * RAG 评测结果持久化：把内存中的 {@link EvalReport} 落库到
 * {@code srm_rag_eval_run}（批次聚合）+ {@code srm_rag_eval_result}（用例明细）。
 * 配合 RabbitMQ 异步评测：先建 PENDING 批次，worker 完成后回写指标与状态。
 */
@Service
@RequiredArgsConstructor
public class RagEvalStoreService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private final RagEvalRunMapper runMapper;
    private final RagEvalResultMapper resultMapper;

    /** 创建「排队中」的评测批次，返回批次 ID。 */
    public Long createPendingRun(String dataset, int topK) {
        RagEvalRun run = new RagEvalRun();
        run.setRunNo("EVAL-" + LocalDate.now().format(DATE_FMT) + "-" + (System.currentTimeMillis() % 1000000));
        run.setDataset(dataset);
        run.setTopK(topK);
        run.setStatus("PENDING");
        run.setTotalCases(0);
        runMapper.insert(run);
        return run.getId();
    }

    /** 标记执行中。 */
    public void markRunning(Long runId) {
        RagEvalRun run = new RagEvalRun();
        run.setId(runId);
        run.setStatus("RUNNING");
        runMapper.updateById(run);
    }

    /** 评测完成：回写聚合指标 + 状态 + 落用例明细。 */
    @Transactional
    public void markCompleted(Long runId, EvalReport report) {
        RagEvalRun run = new RagEvalRun();
        run.setId(runId);
        run.setStatus("COMPLETED");
        run.setTotalCases(report.getTotalCases());
        run.setAvgRecall(report.getAvgRecallAtK());
        run.setAvgPrecision(report.getAvgPrecisionAtK());
        run.setMrr(report.getMrr());
        run.setAvgNdcg(report.getAvgNdcgAtK());
        run.setAvgAccuracy(report.getAvgAccuracy());
        run.setAvgFaithfulness(report.getAvgFaithfulness());
        run.setLatencyP50(toLong(report.getLatencyP50()));
        run.setLatencyP95(toLong(report.getLatencyP95()));
        run.setLatencyP99(toLong(report.getLatencyP99()));
        runMapper.updateById(run);

        List<EvalResult> results = report.getResults();
        if (results != null) {
            for (EvalResult r : results) {
                RagEvalResultEntity e = new RagEvalResultEntity();
                e.setRunId(runId);
                e.setCaseId(r.getCaseId());
                e.setQuestion(r.getQuestion());
                e.setRetrievedDocIds(r.getRetrievedDocIds() == null ? null
                        : JSONUtil.toJsonStr(r.getRetrievedDocIds()));
                e.setRecall(r.getRecallAtK());
                e.setPrecision(r.getPrecisionAtK());
                e.setMrr(r.getReciprocalRank());
                e.setNdcg(r.getNdcgAtK());
                e.setAccuracy(r.getAccuracy());
                e.setFaithfulness(r.getFaithfulness());
                e.setLatencyMs(r.getLatencyMs());
                e.setTags(r.getTags() == null ? null : String.join(",", r.getTags()));
                e.setDifficulty(r.getDifficulty());
                resultMapper.insert(e);
            }
        }
    }

    /** 评测失败。 */
    public void markFailed(Long runId) {
        RagEvalRun run = new RagEvalRun();
        run.setId(runId);
        run.setStatus("FAILED");
        runMapper.updateById(run);
    }

    public List<RagEvalRun> listRuns() {
        return runMapper.selectList(null);
    }

    private Long toLong(Double v) {
        return v == null ? null : Math.round(v);
    }
}
