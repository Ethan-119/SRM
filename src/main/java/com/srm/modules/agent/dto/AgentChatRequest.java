package com.srm.modules.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AgentChatRequest {

    /** 用户问题 */
    private String query;

    /** 会话ID，用于区分不同用户/会话 */
    @JsonProperty("session_id")
    private String sessionId = "default";
}
