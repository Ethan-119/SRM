package com.srm.modules.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * RAG 文档主数据（业务库 rag_document 表）。
 */
@Data
@TableName("rag_document")
public class RagDocument {

    @TableId(type = IdType.INPUT)
    private String id;

    private String title;

    private String content;

    private String source;

    /** 元数据（JSON 字符串） */
    private String metadata;
}
