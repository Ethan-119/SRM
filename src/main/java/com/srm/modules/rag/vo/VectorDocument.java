package com.srm.modules.rag.vo;

/**
 * 向量检索命中的文档片段。
 *
 * @param id         文档片段唯一标识
 * @param content    片段正文
 * @param metadata   元数据（JSON 字符串）
 * @param similarity 余弦相似度（0~1，越大越相关）
 */
public record VectorDocument(String id, String content, String metadata, double similarity) {
}
