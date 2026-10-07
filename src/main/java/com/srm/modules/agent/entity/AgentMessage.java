package com.srm.modules.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话历史消息（长期记忆，落 PG {@code srm_agent_message} 表）。
 */
@Data
@TableName("srm_agent_message")
public class AgentMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 会话 ID */
    private String sessionId;

    /** 角色：user / assistant */
    private String role;

    /** 消息内容 */
    private String content;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
