package com.srm.modules.rag.eval;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用 LLM 判断答案忠实度（可选）。
 * 判断「回答」是否忠实于检索到的「上下文」，未编造事实。
 */
@Service
@RequiredArgsConstructor
public class LlmJudgeService {

    private static final Pattern NUMBER = Pattern.compile("\\d+(\\.\\d+)?");

    private final ChatClient chatClient;

    /**
     * 评估忠实度，返回 0~1 之间的分数。
     */
    public double judgeFaithfulness(String question, String answer, String context) {
        String system = """
                你是 RAG 系统的忠实度评估器。请判断「回答」是否忠实于给定的「上下文」：
                回答中的事实都应能在上下文中找到依据，不得编造。
                只输出一个 0 到 1 之间的数字，1 表示完全忠实，0 表示完全偏离。
                """;
        String text = chatClient.prompt()
                .system(system)
                .user("问题：" + question + "\n上下文：" + context + "\n回答：" + answer)
                .call()
                .content();
        return parseScore(text);
    }

    private double parseScore(String text) {
        if (text == null) {
            return 0.0;
        }
        Matcher m = NUMBER.matcher(text);
        if (!m.find()) {
            return 0.0;
        }
        double v = Double.parseDouble(m.group(1));
        if (v > 1.0) {
            v = v / 100.0; // 兼容 0~100 的输出
        }
        return Math.max(0.0, Math.min(1.0, v));
    }
}
