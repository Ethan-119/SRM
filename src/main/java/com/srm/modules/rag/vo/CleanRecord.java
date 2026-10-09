package com.srm.modules.rag.vo;

import java.util.Map;

/**
 * 清洗后识别出的单条记录（一行 = 一个向量文档）。
 *
 * @param id       文档唯一标识
 * @param title    标题
 * @param content  正文（会被向量化）
 * @param source   来源
 * @param metadata 元数据（可能为 null）
 */
public record CleanRecord(String id, String title, String content, String source, Map<String, Object> metadata) {
}
