package com.srm.modules.rag.vo;

import java.util.List;

/**
 * 数据清洗预览结果（不落库）。
 *
 * @param cleanedText 长文清洗后的全文（记录型时为 null）
 * @param stats       清洗统计
 * @param recordText  是否识别为「逐行一条一档」的记录型数据
 * @param recordCount 识别出的记录条数（非记录型为 0）
 * @param records     逐行记录（非记录型为空列表）
 */
public record CleanPreviewResult(String cleanedText, CleanStats stats, boolean recordText,
                                 int recordCount, List<CleanRecord> records) {
}
