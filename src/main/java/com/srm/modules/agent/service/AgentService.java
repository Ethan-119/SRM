package com.srm.modules.agent.service;

import com.srm.modules.agent.dto.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * 内生智能采购 Agent（Spring AI 内嵌）。
 * 直接调用 Java 业务服务工具，替代原 Python HTTP 透传。
 */
@Service
@RequiredArgsConstructor
public class AgentService {

    private static final String SYSTEM_PROMPT = """
            你是 SRM 供应商管理系统的智能采购助手，帮助采购人员完成询价、比价、
            供应商检索与风险评估。你可以调用工具完成：
            - 供应商检索、物料历史价格、采购成本计算；
            - 正式询比价：当用户要「正式询价/定标/比价选供应商」时，调用正式询比价工具
              （创建询比价单、收集报价、评分/风险/推荐分析），并把推荐结果回传给用户；
            - 基于 Neo4j 知识图谱的智能分析：供应链集中度风险、替代供应商发现、
              供应商关联风险穿透、供应商综合评分、采购员画像。
            当用户要「找出某地区/某品类的供应商并对比评分」时，优先一次性调用「搜索+评分」工具，避免逐个评分。
            当用户需要决策（如选供应商、评估风险、比价定标）时，先调用图谱分析工具获取客观数据，
            再综合给出结论与推荐，并说明理由，供用户做最终决定。
            回答请使用简洁、结构化（分点/表格）的中文，涉及金额时保留两位小数并带货币单位。

            当分析结果适合可视化时，可在正文末尾追加图表数据块（每个块独立用 ```chart 围栏包裹，放在最后）：
            - 仪表盘：{"type":"gauge","title":"标题","value":0.32,"min":0,"max":1,"name":"HHI"}（适合集中度 HHI、综合评分总分；max<=1 时按百分比显示）
            - 雷达图：{"type":"radar","title":"标题","indicators":["价格","质量","交期","服务","风险"],"series":[{"name":"供应商名","values":[80,75,90,60,70]}]}（适合多维度评分）
            - 柱状图：{"type":"bar","title":"标题","categories":["物料A","物料B"],"series":[{"name":"我的采购价","values":[100,200]},{"name":"市场均价","values":[110,215]}]}（适合对比）
            - 饼图：{"type":"pie","title":"标题","data":[{"name":"品类","value":12}]}（适合占比）
            要求：JSON 必须合法；所有数字取自工具返回的真实数据，禁止编造；标题简短。
            """;

    private final ChatClient chatClient;
    private final ChatHistoryService chatHistoryService;

    /**
     * 每个 session 一个信号量（初始 1 许可）：串行化同一会话内的「读历史 + 写历史」，
     * 防止并发消息乱序/竞态。用 {@link Semaphore} 而非 ReentrantLock，因为锁在请求线程获取、
     * 在 Reactor 线程（doFinally）释放，Semaphore 允许跨线程 release。
     */
    private final ConcurrentHashMap<String, Semaphore> sessionLocks = new ConcurrentHashMap<>();

    /** 非流式对话。 */
    public String chat(String query, String sessionId, Long userId) {
        List<Message> history = toAiMessages(chatHistoryService.get(sessionId, userId));
        chatHistoryService.append(sessionId, userId, new ChatMessage("user", query));

        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(history)
                .user(query)
                .call()
                .content();

        chatHistoryService.append(sessionId, userId, new ChatMessage("assistant", answer));
        return answer;
    }

    /** 流式对话（SSE），返回逐 token 的文本流，并在完成/断连时把已生成内容写入历史。 */
    public Flux<String> stream(String query, String sessionId, Long userId) {
        String lockKey = userId + ":" + sessionId;
        Semaphore semaphore = sessionLocks.computeIfAbsent(lockKey, k -> new Semaphore(1));
        semaphore.acquireUninterruptibly();
        try {
            List<Message> history = toAiMessages(chatHistoryService.get(sessionId, userId));
            chatHistoryService.append(sessionId, userId, new ChatMessage("user", query));

            StringBuilder full = new StringBuilder();
            return chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .messages(history)
                    .user(query)
                    .stream()
                    .content()
                    .doOnNext(full::append)
                    .doFinally(sig -> {
                        try {
                            // 完成、取消（断连）、出错都落一次：断连时也能保留已生成的半截内容
                            if (full.length() > 0) {
                                chatHistoryService.append(sessionId, userId,
                                        new ChatMessage("assistant", full.toString()));
                            }
                        } finally {
                            semaphore.release();
                        }
                    });
        } catch (Exception e) {
            semaphore.release();
            throw e;
        }
    }

    public List<ChatMessage> history(String sessionId, Long userId) {
        return chatHistoryService.getFull(sessionId, userId);
    }

    public void clearHistory(String sessionId, Long userId) {
        chatHistoryService.clear(sessionId, userId);
    }

    private List<Message> toAiMessages(List<ChatMessage> messages) {
        return messages.stream().map(m -> {
            if ("user".equals(m.getRole())) {
                return (Message) new UserMessage(m.getContent());
            }
            return new AssistantMessage(m.getContent());
        }).toList();
    }
}
