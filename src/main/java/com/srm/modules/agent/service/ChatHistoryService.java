package com.srm.modules.agent.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.srm.common.CacheConstants;
import com.srm.modules.agent.dto.ChatMessage;
import com.srm.modules.agent.entity.AgentMessage;
import com.srm.modules.agent.mapper.AgentMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 会话历史存储：PG 长期记忆 + Redis 短期窗口。
 *
 * <p>PG 表 {@code srm_agent_message} 保存全量消息（长期记忆，持久化）；
 * Redis LIST（{@code srm:agent:history:{sessionId}}）保存最近 N 条热数据（短期窗口）。
 * 读取时优先命中 Redis，未命中则回源 PG 并回填缓存。</p>
 */
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final AgentMessageMapper agentMessageMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** Redis 短期记忆窗口大小（最近 N 条消息）。 */
    @Value("${srm.agent.history-window:20}")
    private int historyWindow;

    /**
     * 最近 N 条消息（LLM 上下文用）：Redis 优先，未命中回源 PG。
     */
    public List<ChatMessage> get(String sessionId) {
        List<String> cached = stringRedisTemplate.opsForList().range(historyKey(sessionId), 0, -1);
        if (cached != null && !cached.isEmpty()) {
            return cached.stream().map(j -> JSONUtil.toBean(j, ChatMessage.class)).toList();
        }
        List<ChatMessage> recent = loadRecentFromDb(sessionId);
        fillCache(sessionId, recent);
        return recent;
    }

    /**
     * 全量历史（前端展示用）：直接查 PG 长期记忆。
     */
    public List<ChatMessage> getFull(String sessionId) {
        List<AgentMessage> list = agentMessageMapper.selectList(
                Wrappers.<AgentMessage>lambdaQuery()
                        .eq(AgentMessage::getSessionId, sessionId)
                        .orderByAsc(AgentMessage::getId));
        return list.stream().map(this::toChatMessage).toList();
    }

    /**
     * 追加一条消息：先落 PG 长期记忆，再更新 Redis 短期窗口。
     */
    public void append(String sessionId, ChatMessage message) {
        AgentMessage entity = new AgentMessage();
        entity.setSessionId(sessionId);
        entity.setRole(message.getRole());
        entity.setContent(message.getContent());
        agentMessageMapper.insert(entity);

        String key = historyKey(sessionId);
        stringRedisTemplate.opsForList().rightPush(key, JSONUtil.toJsonStr(message));
        stringRedisTemplate.opsForList().trim(key, -historyWindow, -1);
    }

    /**
     * 清空会话：删除 PG 长期记忆 + Redis 短期窗口。
     */
    public void clear(String sessionId) {
        agentMessageMapper.delete(Wrappers.<AgentMessage>lambdaQuery()
                .eq(AgentMessage::getSessionId, sessionId));
        stringRedisTemplate.delete(historyKey(sessionId));
    }

    private List<ChatMessage> loadRecentFromDb(String sessionId) {
        List<AgentMessage> list = agentMessageMapper.selectList(
                Wrappers.<AgentMessage>lambdaQuery()
                        .eq(AgentMessage::getSessionId, sessionId)
                        .orderByDesc(AgentMessage::getId)
                        .last("LIMIT " + historyWindow));
        Collections.reverse(list);
        return list.stream().map(this::toChatMessage).toList();
    }

    private void fillCache(String sessionId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        String key = historyKey(sessionId);
        stringRedisTemplate.opsForList().rightPushAll(
                key, messages.stream().map(JSONUtil::toJsonStr).toList());
        stringRedisTemplate.opsForList().trim(key, -historyWindow, -1);
    }

    private ChatMessage toChatMessage(AgentMessage entity) {
        return new ChatMessage(entity.getRole(), entity.getContent());
    }

    private String historyKey(String sessionId) {
        return CacheConstants.AGENT_HISTORY_KEY + sessionId;
    }
}
