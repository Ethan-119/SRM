package com.srm.modules.rag.eval.model;

/**
 * 评测任务消息（发送到 RabbitMQ 队列）。
 *
 * @param runId 评测批次 ID
 * @param topK  评测 K 值
 */
public record EvalTaskMessage(Long runId, int topK) {
}
