package com.srm.modules.rag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 向量文档写入请求。
 */
@Data
public class RagDocumentDTO {

    /** 文档唯一标识 */
    @NotBlank(message = "文档 id 不能为空")
    private String id;

    /** 文档标题（可选） */
    private String title;

    /** 文档正文（会被向量化） */
    @NotBlank(message = "文档 content 不能为空")
    private String content;

    /** 来源（可选） */
    private String source;

    /** 可选元数据 */
    private Map<String, Object> metadata;
}
