package com.srm.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：异步任务队列 + JSON 消息转换器。
 */
@Configuration
public class RabbitMqConfig {

    /** RAG 评测任务队列 */
    public static final String RAG_EVAL_QUEUE = "srm.rag.eval.queue";

    /** RAG 文档向量化队列 */
    public static final String RAG_DOCUMENT_QUEUE = "srm.rag.document.queue";

    @Bean
    public Queue ragEvalQueue() {
        return new Queue(RAG_EVAL_QUEUE, true);
    }

    @Bean
    public Queue ragDocumentQueue() {
        return new Queue(RAG_DOCUMENT_QUEUE, true);
    }

    /** JSON 消息转换器（发送端 + 监听端共用） */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
