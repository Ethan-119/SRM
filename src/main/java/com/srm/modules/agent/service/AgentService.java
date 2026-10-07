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
            - 供应商检索、物料历史价格、采购成本计算、供应商排名与比价；
            - 基于 Neo4j 知识图谱的智能分析：供应链集中度风险、替代供应商发现、
              供应商关联风险穿透、供应商综合评分、采购员画像。
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

    /** 非流式对话。 */
    public String chat(String query, String sessionId) {
        List<Message> history = toAiMessages(chatHistoryService.get(sessionId));
        chatHistoryService.append(sessionId, new ChatMessage("user", query));

        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(history)
                .user(query)
                .call()
                .content();

        chatHistoryService.append(sessionId, new ChatMessage("assistant", answer));
        return answer;
    }

    /** 流式对话（SSE），返回逐 token 的文本流，并在完成后写入历史。 */
    public Flux<String> stream(String query, String sessionId) {
        List<Message> history = toAiMessages(chatHistoryService.get(sessionId));
        chatHistoryService.append(sessionId, new ChatMessage("user", query));

        StringBuilder full = new StringBuilder();
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .messages(history)
                .user(query)
                .stream()
                .content()
                .doOnNext(full::append)
                .doOnComplete(() -> chatHistoryService.append(sessionId,
                        new ChatMessage("assistant", full.toString())));
    }

    public List<ChatMessage> history(String sessionId) {
        return chatHistoryService.getFull(sessionId);
    }

    public void clearHistory(String sessionId) {
        chatHistoryService.clear(sessionId);
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
