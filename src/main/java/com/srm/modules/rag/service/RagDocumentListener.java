package com.srm.modules.rag.service;

import com.srm.config.RabbitMqConfig;
import com.srm.modules.rag.dto.RagDocumentDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RAG 文档消费者：从 RabbitMQ 消费文档，后台异步向量化并写入主数据 + 向量索引。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagDocumentListener {

    private final RagDocumentService ragDocumentService;

    @RabbitListener(queues = RabbitMqConfig.RAG_DOCUMENT_QUEUE)
    public void onDocumentTask(RagDocumentDTO dto) {
        if (dto == null || dto.getId() == null || dto.getContent() == null) {
            log.warn("收到非法文档消息，忽略");
            return;
        }
        log.info("收到文档向量化任务 id={}", dto.getId());
        try {
            ragDocumentService.add(dto.getId(), dto.getTitle(), dto.getContent(), dto.getSource(), dto.getMetadata());
            log.info("文档向量化完成 id={}", dto.getId());
        } catch (Exception e) {
            log.error("文档向量化失败 id={}", dto.getId(), e);
        }
    }
}
