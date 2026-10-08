package com.srm.modules.rag.vo;

/**
 * 向量检索命中的文档 ID 与相似度（不含正文，正文需回查 rag_document 主数据表）。
 */
public record VectorHit(String docId, double similarity) {
}
