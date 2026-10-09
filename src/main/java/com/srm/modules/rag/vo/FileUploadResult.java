package com.srm.modules.rag.vo;

/**
 * 文件导入结果。
 *
 * @param fileName 原始文件名
 * @param chunks   切分出的文本块数量（= 提交向量化的文档数）
 * @param message  结果说明（成功 / 失败原因）
 */
public record FileUploadResult(String fileName, int chunks, String message) {
}
